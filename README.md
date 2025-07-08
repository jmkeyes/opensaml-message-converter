OpenSAML MessageConverter
=========================

A Spring `MessageConverter` implementation for serializing OpenSAML objects.

Getting Started
----------------

Add the dependency:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.jmkeyes</groupId>
        <artifactId>opensaml-message-converter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

Register the `OpenSAMLObjectMessageConverter` with Spring:

```java
@EnableWebMvc
@Configuration
public class MessageConverterConfiguration implements WebMvcConfigurer {
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
        messageConverters.add(new OpenSAMLMessageConverter());
    }
}
```

Now Spring is capable of understanding the SAML objects that the OpenSAML library produces.

For example:

```java
@Controller
public class MetadataController {
    @GetMapping("/metadata.xml")
    public ResponseEntity<EntityDescriptor> showMetadata() {
        // Build an EntityDescriptor and return it to the requestor.
        return new ResponseEntity<>(metadata, HttpStatus.OK);
    }

    @PostMapping("/sso")
    public ResponseEntity<Response> doSingleSignOn(@ResponseBody Assertion assertion) {
        // Process the Assertion as issue a SAML Response object.
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
 ```

The converter will automatically set a content type for specific types of SAML objects:

  - Assertions: `application/samlassertion+xml`
  - Entity Descriptors: `application/samlmetadata+xml`
  - Default: `application/samlmessage+xml`

This can help make things a bit easier to reason about when integrating SAML functionality. 

Contributing
------------

  1. Clone this repository.
  2. Create your branch: `git checkout -b feature/branch`
  3. Commit your changes: `git commit -am "I am developer."`
  4. Push your changes: `git push origin feature/branch`
  5. Create a PR of your branch against the `main` branch.
