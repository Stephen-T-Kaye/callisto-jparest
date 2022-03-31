package uk.gov.homeoffice.digital.sas.jparest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.homeoffice.digital.sas.jparest.ResourceEndpoint.CALL_ADD_RELATED_ONLY_ON_EXISTING_RESOURCES;
import static uk.gov.homeoffice.digital.sas.jparest.ResourceEndpoint.PATH_ALREADY_EXISTS;
import static uk.gov.homeoffice.digital.sas.jparest.ResourceEndpoint.RELATED_RESOURCE_ALREADY_ADDED;
import static uk.gov.homeoffice.digital.sas.jparest.ResourceEndpoint.RESOURCE_ALREADY_ADDED;
import static uk.gov.homeoffice.digital.sas.jparest.exceptions.addresourcedescriptor.AddResourceDescriptorErrorCode.RELATED_RESOURCE_ALREADY_EXISTS;
import static uk.gov.homeoffice.digital.sas.jparest.exceptions.addresourcedescriptor.AddResourceDescriptorErrorCode.RESOURCE_ALREADY_EXISTS;

import org.junit.jupiter.api.Test;

import uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities.DummyEntityA;
import uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities.DummyEntityB;
import uk.gov.homeoffice.digital.sas.jparest.exceptions.addresourcedescriptor.AddResourceDescriptorErrorCode;
import uk.gov.homeoffice.digital.sas.jparest.exceptions.addresourcedescriptor.AddResourceDescriptorException;

class ResourceEndpointTest {

    private final static Class<DummyEntityA> RESOURCE_CLASS = DummyEntityA.class;
    private final static Class<DummyEntityB> RELATED_RESOURCE_CLASS = DummyEntityB.class;
    private final static Class<Long> ID_FIELD_TYPE = Long.class;
    public static final Class<String> RELATED_ID_FIELD_TYPE = String.class;
    private final static String RESOURCE_PATH = "DummyEntityA";
    private final static String RELATED_RESOURCE_PATH = "dummyEntityBSet";

    @Test
    void add_descriptorsAlreadyContainsResource_exceptionThrown() {
        // Add the resource first
        var resourceEndpoint = new ResourceEndpoint();
        resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE);
        var actualDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS);
        assertThat(actualDescriptor.getPath()).isEqualTo(RESOURCE_PATH);
        assertThat(actualDescriptor.getIdFieldType()).isEqualTo(ID_FIELD_TYPE);

        // Add the resource again
        var thrown = assertThrows(
                AddResourceDescriptorException.class,
                () -> resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE),
                RESOURCE_ALREADY_ADDED);
        assertThat(thrown.getErrorCode()).isEqualTo(RESOURCE_ALREADY_EXISTS.getCode());
    }

    @Test
    void add_pathAllreadyUsed_exceptionThrown() {
        var resourceEndpoint = new ResourceEndpoint();
        resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE);
        ;
        var thrown = assertThrows(
                AddResourceDescriptorException.class,
                () -> resourceEndpoint.add(RELATED_RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE),
                PATH_ALREADY_EXISTS);
        assertThat(thrown.getErrorCode()).isEqualTo(AddResourceDescriptorErrorCode.PATH_ALREADY_EXISTS.getCode());

    }

    @Test
    void add_descriptorsDoesNotContainResource_resourceAdded() {
        var resourceEndpoint = new ResourceEndpoint();
        assertDoesNotThrow(() -> resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE));
        var actualDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS);
        assertThat(actualDescriptor.getPath()).isEqualTo(RESOURCE_PATH);
        assertThat(actualDescriptor.getIdFieldType()).isEqualTo(ID_FIELD_TYPE);
    }

    @Test
    void addRelated_pathAllreadyUsed_exceptionThrown() {
        var resourceEndpoint = new ResourceEndpoint();
        resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE);

        var thrown = assertThrows(
                AddResourceDescriptorException.class,
                () -> resourceEndpoint.addRelated(RESOURCE_CLASS, RELATED_RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE),
                PATH_ALREADY_EXISTS);
        assertThat(thrown.getErrorCode()).isEqualTo(AddResourceDescriptorErrorCode.PATH_ALREADY_EXISTS.getCode());

    }

    @Test
    void addRelated_descriptorsDoesNotContainResource_exceptionThrown() {
        var resourceEndpoint = new ResourceEndpoint();
        var thrown = assertThrows(
                AddResourceDescriptorException.class,
                () -> resourceEndpoint.addRelated(RESOURCE_CLASS, RELATED_RESOURCE_CLASS, RELATED_RESOURCE_PATH,
                        ID_FIELD_TYPE),
                CALL_ADD_RELATED_ONLY_ON_EXISTING_RESOURCES);
        assertThat(thrown.getErrorCode()).isEqualTo(AddResourceDescriptorErrorCode.RESOURCE_DOES_NOT_EXIST.getCode());
        var actualDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS);
        assertThat(actualDescriptor).isNull();
    }

    @Test
    void addRelated_descriptorsContainsTheRelatedResourceAlready_exceptionThrown() {
        // Add resource & related resource first
        var resourceEndpoint = new ResourceEndpoint();
        resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE);
        resourceEndpoint.addRelated(RESOURCE_CLASS, RELATED_RESOURCE_CLASS, RELATED_RESOURCE_PATH,
                RELATED_ID_FIELD_TYPE);
        // Verify resource & related resource exists
        var actualDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS);
        assertThat(actualDescriptor.getPath()).isEqualTo(RESOURCE_PATH);
        assertThat(actualDescriptor.getIdFieldType()).isEqualTo(ID_FIELD_TYPE);
        var relatedDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS).getRelations()
                .get(RELATED_RESOURCE_CLASS);
        assertThat(relatedDescriptor.getPath()).isEqualTo(RELATED_RESOURCE_PATH);
        assertThat(relatedDescriptor.getIdFieldType()).isEqualTo(RELATED_ID_FIELD_TYPE);

        // Try to add related resource again
        var thrown = assertThrows(
                AddResourceDescriptorException.class,
                () -> resourceEndpoint.addRelated(RESOURCE_CLASS, RELATED_RESOURCE_CLASS, RELATED_RESOURCE_PATH,
                        RELATED_ID_FIELD_TYPE),
                RELATED_RESOURCE_ALREADY_ADDED);
        assertThat(thrown.getErrorCode()).isEqualTo(RELATED_RESOURCE_ALREADY_EXISTS.getCode());
    }

    @Test
    void addRelated_descriptorContainsTheResourceButNotRelatedResource_relatedResourceAdded() {
        // Add resource
        var resourceEndpoint = new ResourceEndpoint();
        resourceEndpoint.add(RESOURCE_CLASS, RESOURCE_PATH, ID_FIELD_TYPE);
        // Verify resource & related resource does not exists
        var actualDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS);
        assertThat(actualDescriptor.getPath()).isEqualTo(RESOURCE_PATH);
        assertThat(actualDescriptor.getIdFieldType()).isEqualTo(ID_FIELD_TYPE);
        var relatedDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS).getRelations()
                .get(RELATED_RESOURCE_CLASS);
        assertThat(relatedDescriptor).isNull();

        // Add related resource
        resourceEndpoint.addRelated(RESOURCE_CLASS, RELATED_RESOURCE_CLASS, RELATED_RESOURCE_PATH,
                RELATED_ID_FIELD_TYPE);
        relatedDescriptor = resourceEndpoint.getDescriptors().get(RESOURCE_CLASS).getRelations()
                .get(RELATED_RESOURCE_CLASS);
        assertThat(relatedDescriptor.getPath()).isEqualTo(RELATED_RESOURCE_PATH);
        assertThat(relatedDescriptor.getIdFieldType()).isEqualTo(String.class);
    }

}