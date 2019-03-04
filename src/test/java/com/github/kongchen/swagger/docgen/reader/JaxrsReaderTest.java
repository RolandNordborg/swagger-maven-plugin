package com.github.kongchen.swagger.docgen.reader;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import java.util.Map;
import org.apache.maven.plugin.logging.Log;

import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

public class JaxrsReaderTest {
    @Mock
    private Log log;

    private JaxrsReader reader;

    List<SwaggerExtension> extensions = SwaggerExtensions.getExtensions();

    @BeforeMethod
    public void setup() {
        reader = new JaxrsReader(new Swagger(), log);
    }

    @AfterMethod
    public void resetExtenstions() {
        SwaggerExtensions.setExtensions(extensions);
    }

    @Test
    public void ignoreClassIfNoApiAnnotation() {
        Swagger result = reader.read(NotAnnotatedApi.class);

        assertEmptySwaggerResponse(result);
    }

    @Test
    public void ignoreApiIfHiddenAttributeIsTrue() {
        Swagger result = reader.read(HiddenApi.class);

        assertEmptySwaggerResponse(result);
    }

    @Test
    public void includeApiIfHiddenParameterIsTrueAndApiHiddenAttributeIsTrue() {
        Swagger result = reader.read(HiddenApi.class, "", null, true, new String[0], new String[0], new HashMap<String, Tag>(), new ArrayList<Parameter>());

        assertNotNull(result, "No Swagger object created");
        assertFalse(result.getTags().isEmpty(), "Should contain api tags");
        assertFalse(result.getPaths().isEmpty(), "Should contain operation paths");
    }

    @Test
    public void discoverApiOperation() {
        Tag expectedTag = new Tag();
        expectedTag.name("atag");
        Swagger result = reader.read(AnApi.class);

        assertSwaggerResponseContents(expectedTag, result);
    }

    @Test
    public void createNewSwaggerInstanceIfNoneProvided() {
        JaxrsReader nullReader = new JaxrsReader(null, log);
        Tag expectedTag = new Tag();
        expectedTag.name("atag");
        Swagger result = nullReader.read(AnApi.class);

        assertSwaggerResponseContents(expectedTag, result);
    }

    @Test
    public void handleResponseWithInheritance() {
        Swagger result = reader.read(AnApiWithInheritance.class);
        Map<String, Model> models = result.getDefinitions();

        Map<String, Property> properties = getProperties(models, "SomeResponseWithAbstractInheritance");
        assertNotNull(properties);
        assertTrue(properties.containsKey("classProperty"));
        assertFalse(properties.containsKey("inheritedProperty"));
        assertFalse(properties.containsKey("type"));

        properties = models.get("SomeResponseBaseClass").getProperties();
        assertNotNull(properties);
        assertTrue(properties.containsKey("inheritedProperty"));
        assertTrue(properties.containsKey("type"));

        properties = getProperties(models, "SomeResponseWithInterfaceInheritance");
        assertNotNull(properties);
        assertTrue(properties.containsKey("classProperty"));
        assertFalse(properties.containsKey("inheritedProperty"));
        assertFalse(properties.containsKey("type"));

        properties = getProperties(models,"SomeResponseInterface");
        assertNotNull(properties);
        assertTrue(properties.containsKey("inheritedProperty"));
        assertTrue(properties.containsKey("type"));

        properties = getProperties(models,"SomeResponse");
        assertNotNull(properties);
        assertTrue(properties.containsKey("classProperty"));
        assertTrue(properties.containsKey("type"));

        properties = getProperties(models,"SomeOtherResponse");
        assertNotNull(properties);
        assertTrue(properties.containsKey("classProperty"));
        assertTrue(properties.containsKey("type"));
    }

    private Map<String, Property> getProperties(Map<String, Model> models, String className) {
        assertTrue(models.containsKey(className));

        Model model = models.get(className);
        if (model instanceof ComposedModel) {
            ComposedModel composedResponse = (ComposedModel) model;
            return composedResponse.getChild().getProperties();
        } else {
            return model.getProperties();
        }
    }

    private void assertEmptySwaggerResponse(Swagger result) {
        assertNotNull(result, "No Swagger object created");
        assertNull(result.getTags(), "Should not have any tags");
        assertNull(result.getPaths(), "Should not have any paths");
    }

    private void assertSwaggerResponseContents(Tag expectedTag, Swagger result) {
        assertNotNull(result, "No Swagger object created");
        assertFalse(result.getTags().isEmpty(), "Should contain api tags");
        assertTrue(result.getTags().contains(expectedTag), "Expected tag missing");
        assertFalse(result.getPaths().isEmpty(), "Should contain operation paths");
        assertTrue(result.getPaths().containsKey("/apath"), "Path missing from paths map");
        io.swagger.models.Path path = result.getPaths().get("/apath");
        assertFalse(path.getOperations().isEmpty(), "Should be a get operation");
    }

    @Api(tags = "atag")
    @Path("/apath")
    static class AnApi {
        @ApiOperation(value = "Get a model.")
        @GET
        public Response getOperation() {
            return Response.ok().build();
        }
    }

    @Api(hidden = true, tags = "atag")
    @Path("/hidden/path")
    static class HiddenApi {
        @ApiOperation(value = "Get a model.")
        @GET
        public Response getOperation() {
            return Response.ok().build();
        }
    }

    @Path("/apath")
    static class NotAnnotatedApi {
    }

    @Api
    @Path("/apath")
    static class AnApiWithInheritance {
        @GET
        public SomeResponseWithAbstractInheritance getOperation() {
            return null;
        }

        @GET
        public SomeResponseBaseClass getOperation2() { return null; }

        @GET
        public SomeResponseWithInterfaceInheritance getOperation3() { return null; }

        @GET
        public SomeResponseInterface getOperation4() {
            return null;
        }

        @GET
        public List<SomeResponse> getOperation5() { return null; }

        @GET
        public SomeOtherResponse[] getOperation6() { return null; }
    }

    @JsonTypeInfo(use=Id.NAME, property="type")
    static class SomeResponseWithAbstractInheritance extends SomeResponseBaseClass {
        public String getClassProperty(){
            return null;
        }
    }

    @JsonTypeInfo(use=Id.NAME, property="type")
    @JsonSubTypes({
            @JsonSubTypes.Type(SomeResponseWithAbstractInheritance.class)
    })
    static abstract class SomeResponseBaseClass {
        public String getInheritedProperty(){
            return null;
        }
    }

    @JsonTypeInfo(use=Id.NAME, property="type")
    static class SomeResponseWithInterfaceInheritance implements SomeResponseInterface {
        public String getClassProperty(){
            return null;
        }
        public String getInheritedProperty(){
            return null;
        }
    }

    @JsonTypeInfo(use=Id.NAME, property="type")
    @JsonSubTypes({
            @JsonSubTypes.Type(SomeResponseWithInterfaceInheritance.class)
    })
    interface SomeResponseInterface {
        String getInheritedProperty();
    }

    @JsonTypeInfo(use=Id.NAME, property="type")
    static class SomeResponse {
        public String getClassProperty() { return null; }
    }

    @JsonTypeInfo(use=Id.NAME, property="type")
    static class SomeOtherResponse {
        public String getClassProperty() { return null; }
    }
}
