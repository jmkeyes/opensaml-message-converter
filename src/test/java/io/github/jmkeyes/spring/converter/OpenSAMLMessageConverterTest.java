package io.github.jmkeyes.spring.converter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObjectBuilder;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class OpenSAMLMessageConverterTest {
    /**
     * The following values should match the values in the fixture.
     */
    public static class AuthnRequestFixture {
        public static final String ID = "SUPERNONCE_0000000000000000000000000000000000000000";
        public static final SAMLVersion VERSION = SAMLVersion.VERSION_20;
        public static final String PROVIDER_NAME = "Example Service Provider";
        public static final Instant ISSUE_INSTANT = Instant.parse("2024-04-01T00:00:00Z");
        public static final String DESTINATION = "http://idp.example.com/sso";
        public static final String ACS_URL = "http://sp.example.com/acs";
        public static final String ISSUER = "http://sp.example.com/metadata.xml";
        public static final String NAMEID_FORMAT = NameID.EMAIL;
    }

    /**
     * Ensure that OpenSAML components have been initialized.
     */
    @BeforeAll
    public static void initialize() throws InitializationException {
        InitializationService.initialize();
    }

    /**
     * Make sure we don't accidentally try to handle non-OpenSAML objects.
     */
    @Test
    void ensureConverterSupportsSamlObjectsExclusively() {
        final OpenSAMLMessageConverter converter = new OpenSAMLMessageConverter();

        // Valid SAML objects.
        assertTrue(converter.supports(Assertion.class));
        assertTrue(converter.supports(EntityDescriptor.class));

        // Not SAML objects.
        assertFalse(converter.supports(String.class));
        assertFalse(converter.supports(HashMap.class));
    }

    /**
     * Read a SAML message from the HTTP request body.
     */
    @Test
    void unmarshallFromXml() {
        final InputStream authnRequestFixture = this.getClass().getResourceAsStream("/AuthnRequest.xml");

        if (authnRequestFixture == null) {
            fail("Unable to find AuthnRequest.xml fixture in test resources.");
        }

        final OpenSAMLMessageConverter converter = new OpenSAMLMessageConverter();

        try {
            // Try to parse the message from its original XML definition.
            final MockHttpInputMessage inputMessage = new MockHttpInputMessage(authnRequestFixture);
            AuthnRequest requestFromXml = (AuthnRequest) converter.readInternal(AuthnRequest.class, inputMessage);

            assertEquals(AuthnRequestFixture.ID, requestFromXml.getID());
            assertEquals(AuthnRequestFixture.VERSION, requestFromXml.getVersion());
            assertEquals(AuthnRequestFixture.PROVIDER_NAME, requestFromXml.getProviderName());
            assertEquals(AuthnRequestFixture.ISSUE_INSTANT, requestFromXml.getIssueInstant());
            assertEquals(AuthnRequestFixture.DESTINATION, requestFromXml.getDestination());
            assertEquals(AuthnRequestFixture.ACS_URL, requestFromXml.getAssertionConsumerServiceURL());
            assertEquals(AuthnRequestFixture.ISSUER, requestFromXml.getIssuer().getValue());
            assertEquals(AuthnRequestFixture.NAMEID_FORMAT, requestFromXml.getNameIDPolicy().getFormat());
        } catch (IOException | HttpMessageNotReadableException e) {
            fail("Could not read the AuthnRequest from XML fixture.");
        }
    }

    /**
     * Helper to build SAMLObjects for testing. Requires OpenSAML to be initialized.
     */
    @SuppressWarnings("unchecked")
    private <T extends SAMLObject> T buildSamlObject(Class<T> cls) {
        try {
            XMLObjectProviderRegistry registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
            XMLObjectBuilderFactory builderFactory = registry.getBuilderFactory();

            QName defaultElementName = (QName) cls.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
            XMLObjectBuilder<?> builder = builderFactory.getBuilder(defaultElementName);

            if (builder == null) {
                throw new RuntimeException("No builder available for this object.");
            }

            return (T) builder.buildObject(defaultElementName);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            // All SAMLObjects have a DEFAULT_ELEMENT_NAME.
            throw new RuntimeException("DEFAULT_ELEMENT_NAME not found.", e);
        }
    }

    /**
     * Make sure the converter selects the proper content type for a message.
     */
    @Test
    void selectsAppropriateContentType() {
        final OpenSAMLMessageConverter converter = new OpenSAMLMessageConverter();

        final Assertion assertion = buildSamlObject(Assertion.class);
        assertEquals(converter.getDefaultContentType(assertion), OpenSAMLMessageConverter.SAML_ASSERTION);

        final EntityDescriptor descriptor = buildSamlObject(EntityDescriptor.class);
        assertEquals(converter.getDefaultContentType(descriptor), OpenSAMLMessageConverter.SAML_METADATA);

        final AuthnRequest authnRequest = buildSamlObject(AuthnRequest.class);
        assertEquals(converter.getDefaultContentType(authnRequest), OpenSAMLMessageConverter.SAML_MESSAGE);
    }

    /**
     * Serialize a SAMLObject onto the wire with the appropriate content type.
     */
    @Test
    void marshallToXml() {
        final OpenSAMLMessageConverter converter = new OpenSAMLMessageConverter();

        AuthnRequest fixture = buildSamlObject(AuthnRequest.class);

        fixture.setID(AuthnRequestFixture.ID);
        fixture.setVersion(AuthnRequestFixture.VERSION);
        fixture.setProviderName(AuthnRequestFixture.PROVIDER_NAME);
        fixture.setIssueInstant(AuthnRequestFixture.ISSUE_INSTANT);
        fixture.setDestination(AuthnRequestFixture.DESTINATION);
        fixture.setAssertionConsumerServiceURL(AuthnRequestFixture.ACS_URL);

        Issuer issuer = buildSamlObject(Issuer.class);
        issuer.setValue(AuthnRequestFixture.ISSUER);
        fixture.setIssuer(issuer);

        NameIDPolicy nameIDPolicy = buildSamlObject(NameIDPolicy.class);
        nameIDPolicy.setFormat(AuthnRequestFixture.NAMEID_FORMAT);
        fixture.setNameIDPolicy(nameIDPolicy);

        try {
            MockHttpOutputMessage outputMessage = new MockHttpOutputMessage();
            converter.writeInternal(fixture, outputMessage);

            // Ensure the message has the correct content type.
            final MediaType contentType = outputMessage.getHeaders().getContentType();
            assertEquals(OpenSAMLMessageConverter.SAML_MESSAGE, contentType);

            // Now round-trip the message back through the converter to avoid comparing strings.
            final MockHttpInputMessage inputMessage = new MockHttpInputMessage(outputMessage.getBodyAsBytes());
            AuthnRequest authnRequest = (AuthnRequest) converter.readInternal(AuthnRequest.class, inputMessage);

            assertEquals(AuthnRequestFixture.ID, authnRequest.getID());
            assertEquals(AuthnRequestFixture.VERSION, authnRequest.getVersion());
            assertEquals(AuthnRequestFixture.PROVIDER_NAME, authnRequest.getProviderName());
            assertEquals(AuthnRequestFixture.ISSUE_INSTANT, authnRequest.getIssueInstant());
            assertEquals(AuthnRequestFixture.DESTINATION, authnRequest.getDestination());
            assertEquals(AuthnRequestFixture.ACS_URL, authnRequest.getAssertionConsumerServiceURL());
            assertEquals(AuthnRequestFixture.ISSUER, fixture.getIssuer().getValue());
            assertEquals(AuthnRequestFixture.NAMEID_FORMAT, fixture.getNameIDPolicy().getFormat());
        } catch (IOException | HttpMessageNotWritableException e) {
            fail("Could not write AuthnRequest as XML.");
        }
    }
}