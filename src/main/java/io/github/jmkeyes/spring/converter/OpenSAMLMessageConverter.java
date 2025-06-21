package io.github.jmkeyes.spring.converter;

import net.shibboleth.utilities.java.support.xml.ParserPool;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A Spring Framework MessageConverter implementation for OpenSAML objects.
 */
public class OpenSAMLMessageConverter extends AbstractHttpMessageConverter<SAMLObject> {
    // The following are various MIME types for SAML messages.
    static final MediaType SAML_MESSAGE = new MediaType("application", "samlmessage+xml");
    static final MediaType SAML_METADATA = new MediaType("application", "samlmetadata+xml");
    static final MediaType SAML_ASSERTION = new MediaType("application", "samlassertion+xml");

    private final ParserPool parserPool;

    /**
     * Creates a message converter using an OpenSAML ParserPool from the ConfigurationService.
     */
    public OpenSAMLMessageConverter() {
        super(StandardCharsets.UTF_8, SAML_MESSAGE, SAML_METADATA, SAML_ASSERTION);
        XMLObjectProviderRegistry registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
        this.parserPool = registry.getParserPool();
    }

    /**
     * Creates a message converter from an initialized OpenSAML ParserPool.
     *
     * @param parserPool An initialized OpenSAML ParserPool.
     */
    public OpenSAMLMessageConverter(ParserPool parserPool) {
        super(StandardCharsets.UTF_8, SAML_MESSAGE, SAML_METADATA, SAML_ASSERTION);
        this.parserPool = parserPool;
    }

    /**
     * Support any object derived from SAMLObject.
     */
    @Override
    protected boolean supports(@NonNull Class<?> klass) {
        return SAMLObject.class.isAssignableFrom(klass);
    }

    /**
     * Read an XML document from the HTTP message body and convert it to a SAMLObject.
     */
    @NonNull
    @Override
    protected SAMLObject readInternal(@NonNull Class<? extends SAMLObject> klass, @NonNull HttpInputMessage message) throws IOException, HttpMessageNotReadableException {
        try {
            return klass.cast(XMLObjectSupport.unmarshallFromInputStream(parserPool, message.getBody()));
        } catch (UnmarshallingException e) {
            throw new HttpMessageNotReadableException("Unable to unmarshal SAML object.", message);
        } catch (XMLParserException e) {
            throw new HttpMessageNotReadableException("Unable to parse XML object.", message);
        } catch (IOException e) {
            throw new HttpMessageNotReadableException("Cannot open input stream.", message);
        }
    }

    /**
     * Guess at the appropriate MediaType for a given SAML object type.
     */
    @Override
    protected MediaType getDefaultContentType(SAMLObject object) {
        // There are better ways of determining a content type to use.
        return switch (object.getElementQName().getLocalPart()) {
            // XXX: Only handles SAML 2.x Assertions (which should be fine)
            case Assertion.DEFAULT_ELEMENT_LOCAL_NAME -> SAML_ASSERTION;
            // XXX: Technically this could also be multiple EntityDescriptors.
            case EntityDescriptor.DEFAULT_ELEMENT_LOCAL_NAME -> SAML_METADATA;
            // XXX: Generic fallback for anything derived from SAMLObject.
            default -> SAML_MESSAGE;
        };
    }

    /**
     * Convert a SAMLObject into it's XML representation and write it to the HTTP message body.
     */
    @Override
    protected void writeInternal(@NonNull SAMLObject object, @NonNull HttpOutputMessage message) throws HttpMessageNotWritableException {
        // Set the Content-Type header if it hasn't been set already.
        if (message.getHeaders().getContentType() == null) {
            final MediaType contentType = getDefaultContentType(object);
            message.getHeaders().setContentType(contentType);
        }

        try {
            XMLObjectSupport.marshallToOutputStream(object, message.getBody());
        } catch (MarshallingException e) {
            throw new HttpMessageNotWritableException("Unable to marshal SAML object.", e);
        } catch (IOException e) {
            throw new HttpMessageNotWritableException("Cannot open output stream.", e);
        }
    }
}
