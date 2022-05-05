package uk.gov.homeoffice.digital.sas.jparest.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.homeoffice.digital.sas.jparest.EntityUtils;
import uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities.DummyEntityA;
import uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities.DummyEntityB;
import uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities.DummyEntityC;
import uk.gov.homeoffice.digital.sas.jparest.entityutils.testentities.DummyEntityD;
import uk.gov.homeoffice.digital.sas.jparest.exceptions.ResourceConstraintViolationException;
import uk.gov.homeoffice.digital.sas.jparest.exceptions.ResourceNotFoundException;
import uk.gov.homeoffice.digital.sas.jparest.exceptions.UnknownResourcePropertyException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import static org.junit.jupiter.api.Named.named;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@Transactional
@ContextConfiguration(locations = "/test-context.xml")
class ResourceApiControllerTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String RESOURCE_NOT_FOUND_ERROR_FORMAT = "Resource with id: %s was not found";
    private static final String RELATED_RESOURCE_NOT_FOUND_ERROR_FORMAT = "No related %s resources removed as the following resources could not be found. Ids:[%s]";

    // region list

    @Test
    void list_withoutFilter_returnsAllEntities() {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        var response = controller.list(null, Pageable.ofSize(100));

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(10);
    }

    @ParameterizedTest
    @MethodSource("filters")
    void list_withFilter_returnsFilteredEntities(SpelExpression expression, int expectedItems) {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        var response = controller.list(expression, Pageable.ofSize(100));

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(expectedItems);
    }

    @Test
    void list_sorted_returnsItemsSortedInCorrectDirection() {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        var sort = Sort.by(Direction.ASC, "id");
        var pageable = PageRequest.ofSize(100).withSort(sort);

        var response = controller.list(null, pageable);
        final var items = response.getItems().toArray(new DummyEntityA[0]);

        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
        IntStream.range(1, items.length).forEach(i -> {
            assertThat(items[i].getId()).isGreaterThan(items[i-1].getId());
        });

        sort = Sort.by(Direction.DESC, "id");
        pageable = PageRequest.ofSize(100).withSort(sort);

        response = controller.list(null, pageable);
        final var items2 = response.getItems().toArray(new DummyEntityA[0]);

        assertThat(items2).hasSizeGreaterThanOrEqualTo(2);
        IntStream.range(1, items2.length).forEach(i -> {
            assertThat(items2[i].getId()).isLessThan(items2[i-1].getId());
        });
    }

    // endregion

    // region get

    @Test
    void get_resourceWithIdExists_returnsEntity() {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        var apiResponse = controller.get(2);
        var dummy = apiResponse.getItems().get(0);

        assertThat(apiResponse.getItems()).hasSize(1);
        assertThat(dummy.getId()).isEqualTo(2);
    }

    @Test
    void get_idIsNull_throwsIllegalArugmentException() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> controller.get(null))
                .withMessage("identifier must not be null");
    }

    @ParameterizedTest
    @MethodSource("invalidIDSource")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void get_idCantBeConvertedToExpectedType_throwsTypeMismatchException(Class<?> clazz, Object id) {
        ResourceApiController controller = getResourceApiController(DummyEntityA.class, clazz);
        assertThatExceptionOfType(TypeMismatchException.class).isThrownBy(() -> controller.get(id));
    }

    // endregion

    // region create

    @Test
    @Transactional
    void create_resourceIsValid_resourceIsPersisted() throws JsonProcessingException {
        String payload = "{\n" +
                "            \"id\": -1\n" +
                "        }";

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> controller.get(-1));

        var apiResponse = controller.create(payload);
        var dummy = apiResponse.getItems().get(0);

        assertThat(apiResponse.getItems()).hasSize(1);
        assertThat(dummy).isNotNull();
        assertThat(dummy.getId()).isNotNull();

        var retryResponse = controller.get(-1);
        var retryResource = retryResponse.getItems().get(0);
        assertThat(retryResource).isEqualTo(dummy);

    }

    @Test
    void create_emptyPayload_jsonExceptionThrown() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(JsonProcessingException.class).isThrownBy(() -> controller.create(""));
    }

    @Test
    void create_invalidPayload_persistenceExceptionThrown() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(PersistenceException.class).isThrownBy(() -> controller.create("{}"));
    }

    @Test
    void create_unrecognizedPropertyOnPayload_unknownResourcePropertyExceptionThrown() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(UnknownResourcePropertyException.class).isThrownBy(() -> controller.create("{\"otherUnknownProperty\": 1}"));
    }

    @Test
    void create_payloadViolatesEntityConstraints_resourceConstraintViolationExceptionThrown() {
        var controller = getResourceApiController(DummyEntityD.class, Integer.class);
        assertThatExceptionOfType(ResourceConstraintViolationException.class).isThrownBy(() -> controller.create("{}"))
                .withMessageContainingAll("description", "telephone", "has the following error(s):");
    }

    // endregion

    // region update

    @Test
    @Transactional
    void update_resourceExists_persistsChanges() throws JsonProcessingException {

        String payload = "{" +
                "            \"id\": 100," +
                "            \"description\": \"Dummy Entity C 100\"," +
                "            \"index\": 1" +
                "        }";

        String updatedPayload = "{" +
                "            \"id\": 100," +
                "            \"description\": \"Updated Dummy Entity C 100\"," +
                "            \"index\": 2" +
                "        }";

        var controller = getResourceApiController(DummyEntityC.class, Integer.class);

        assertThatNoException().isThrownBy(() -> controller.create(payload));
        var getResponse = controller.get(100);
        var getResource = getResponse.getItems().get(0);
        assertThat(getResource.getDescription()).isEqualTo("Dummy Entity C 100");

        var updateResponse = controller.update(100, updatedPayload);
        var dummy = updateResponse.getItems().get(0);

        assertThat(updateResponse.getItems()).hasSize(1);
        assertThat(dummy).isNotNull();
        assertThat(dummy.getId()).isEqualTo(100);
        assertThat(dummy.getIndex()).isEqualTo(2);
        assertThat(dummy.getDescription()).isEqualTo("Updated Dummy Entity C 100");

        var checkResponse = controller.get(100);
        var checkResource = checkResponse.getItems().get(0);
        assertThat(checkResource).isEqualTo(dummy);

    }

    @ParameterizedTest(name="{0}")
    @MethodSource("invalidPayloads")
    @Transactional
    void update_resourceExistsInvalidPayload_jsonExceptionThrown(String payload) {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(JsonProcessingException.class).isThrownBy(() -> controller.update(1, payload));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("invalidPayloads")
    @Transactional
    void update_resourceDoesntExistInvalidPayload_jsonExceptionThrown(String payload) {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> controller.get(-1));
        assertThatExceptionOfType(JsonProcessingException.class).isThrownBy(() -> controller.update(-1, payload));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("invalidProperty")
    void update_unrecognizedPropertyOnPayload_unknownResourcePropertyExceptionThrown(String payload) {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(UnknownResourcePropertyException.class).isThrownBy(() -> controller.update(-1, payload));
    }

    @Test
    @Transactional
    void update_resourceDoesntExist_resourceNotFoundExceptionThrown() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> controller.update(-1, "{}"));
    }

    @Test
    @Transactional
    void update_payloadIdDoesNotMatchUrlPathId_throwsError() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        var payload = "{\"id\": 2 }";
        assertThatIllegalArgumentException()
                .isThrownBy(() -> controller.update(1, payload))
                .withMessageContaining("payload resource id value must match the url id");
    }

    @Test
    @Transactional
    void update_payloadOmitsId_noIdMissMatchErrorThrown() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);
        assertThatNoException().isThrownBy(() -> controller.update(1, "{}"));
    }

    @Test
    void update_payloadViolatesEntityConstraints_resourceConstraintViolationExceptionThrown() {
        var controller = getResourceApiController(DummyEntityD.class, Integer.class);
        assertThatExceptionOfType(ResourceConstraintViolationException.class).isThrownBy(() -> controller.update(-1, "{}"))
                .withMessageContainingAll("description", "telephone", "has the following error(s):");
    }

    // endregion

    // region delete

    @Test
    @Transactional
    void delete_resourceExists_resourceIsDeleted() {
        String payload = "{" +
                "            \"id\": 100," +
                "            \"description\": \"Dummy Entity C 100\"," +
                "            \"index\": 1" +
                "        }";

        var controller = getResourceApiController(DummyEntityC.class, Integer.class);
        assertThatNoException().isThrownBy(() -> controller.create(payload));
        assertThatNoException().isThrownBy(() -> controller.get(100));
        assertThatNoException().isThrownBy(() -> controller.delete(100));
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> controller.get(100));
    }

    @Test
    @Transactional
    void delete_resourceDoesNotExist_resourceNotFoundExceptionThrown() {

        var controller = getResourceApiController(DummyEntityC.class, Integer.class);
        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> controller.delete(-1))
                .withMessage(String.format(RESOURCE_NOT_FOUND_ERROR_FORMAT, -1));
    }

    // endregion

    // region addRelated

    @Test
    @Transactional
    @SuppressWarnings("unchecked")
    void addRelated_allResourcesExist_addsRelatedItems() {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        var getRelatedResponse = controller.getRelated(10, "dummyEntityBSet", null, Pageable.ofSize(100));
        assertThat(getRelatedResponse.getItems()).isEmpty();

        assertThatNoException()
                .isThrownBy(() -> controller.addRelated(10, "dummyEntityBSet", new Object[] { 1 }));

        getRelatedResponse = controller.getRelated(10, "dummyEntityBSet", null, Pageable.ofSize(100));
        assertThat(getRelatedResponse.getItems()).hasSize(1);
        var resource = (DummyEntityB) getRelatedResponse.getItems().get(0);
        assertThat(resource.getId()).isEqualTo(1);

    }

    @Test
    @Transactional
    void addRelated_resourceDoesntExist_resourceNotFoundExceptionThrown() {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatExceptionOfType(ResourceNotFoundException.class)
        	.isThrownBy(() -> controller.addRelated(-1, "dummyEntityBSet", new Object[] { 1 }))
                .withMessage(String.format(RESOURCE_NOT_FOUND_ERROR_FORMAT, -1));
    }

    @Test
    @Transactional
    void addRelated_relatedResourceDoesntExist_resourceNotFoundExceptionThrown() {
        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatNoException().isThrownBy(() -> controller.get(1));

        assertThatExceptionOfType(ResourceNotFoundException.class)
        	.isThrownBy(() ->  controller.addRelated(1, "dummyEntityBSet", new Object[] { -1 }))
                .withMessage(String.format(RESOURCE_NOT_FOUND_ERROR_FORMAT, 1));

    }

    // endregion

    // region getRelated

    @ParameterizedTest
    @MethodSource("relatedResourceFilters")
    @SuppressWarnings("unchecked")
    void getRelated_filterExpressionProvided_returnsFilteredResources(int resourceId, SpelExpression expression,
            int expectedItems) {

        var controller = getResourceApiController(DummyEntityA.class, Integer.class);

        var apiResponse = controller.getRelated(resourceId, "dummyEntityBSet", expression, Pageable.ofSize(100));

        assertThat(apiResponse).isNotNull();
        assertThat(apiResponse.getItems()).hasSize(expectedItems);

    }

    // endregion

    // region deleteRelated

    @Test
    @Transactional
    void deleteRelated_relationshipExists_deletesRelationshipButAllResourcesStillExist() {

        var controllerA = getResourceApiController(DummyEntityA.class, Integer.class);

        var getRelatedResponse = controllerA.getRelated(2, "dummyEntityBSet", null, Pageable.ofSize(100));
        @SuppressWarnings("unchecked")
        var items = (List<DummyEntityB>) getRelatedResponse.getItems();
        assertThat(items).isNotEmpty()
                .anyMatch((item) -> item.getId().equals(2L));

        assertThatNoException().isThrownBy(
                () -> controllerA.deleteRelated(2, "dummyEntityBSet", new Object[] { 2 }));

        getRelatedResponse = controllerA.getRelated(2, "dummyEntityBSet", null, Pageable.ofSize(100));
        @SuppressWarnings("unchecked")
        var checkItems = (List<DummyEntityB>) getRelatedResponse.getItems();
        assertThat(checkItems).noneMatch((item) -> item.getId().equals(2L));

        var controllerB = getResourceApiController(DummyEntityB.class, Integer.class);
        assertThatNoException().isThrownBy(() -> controllerB.get(2));

    }

    @Test
    @Transactional
    void deleteRelated_resourceDoesntExist_resourceNotFoundExceptionThrown() {

        var controllerA = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> controllerA.get(-1));

        assertThatExceptionOfType(ResourceNotFoundException.class)
        	.isThrownBy(() ->  controllerA.deleteRelated(-1, "dummyEntityBSet", new Object[] { 2 }))
                .withMessage(String.format(RESOURCE_NOT_FOUND_ERROR_FORMAT, -1));
    }

    @Test
    @Transactional
    void deleteRelated_resourceExistsButRelationshipDoesnt_resourceNotFoundExceptionThrown() {

        var controllerA = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatNoException().isThrownBy(() -> controllerA.get(1));

        var getRelatedResponse = controllerA.getRelated(1, "dummyEntityBSet", null, Pageable.ofSize(100));
        @SuppressWarnings("unchecked")
        var checkItems = (List<DummyEntityB>) getRelatedResponse.getItems();
        assertThat(checkItems).noneMatch((item) -> item.getId().equals(-1L));

        assertThatExceptionOfType(ResourceNotFoundException.class)
        	.isThrownBy(() -> controllerA.deleteRelated(1, "dummyEntityBSet", new Object[] { -1 }))
                .withMessage(String.format(RELATED_RESOURCE_NOT_FOUND_ERROR_FORMAT, DummyEntityB.class, -1));
    }

    @Test
    @Transactional
    void deleteRelated_resourceExists_relatedIdsResultInFoundAndNotFound_exceptionIncludesNotFoundIdsInMessage() {

        var controllerA = getResourceApiController(DummyEntityA.class, Integer.class);

        assertThatNoException().isThrownBy(() -> controllerA.get(1));

        var getRelatedResponse = controllerA.getRelated(1, "dummyEntityBSet", null, Pageable.ofSize(100));
        @SuppressWarnings("unchecked")
        var checkItems = (List<DummyEntityB>) getRelatedResponse.getItems();
        assertThat(checkItems)
                .noneMatch((item) -> item.getId().equals(-1L))
                .noneMatch((item) -> item.getId().equals(-2L))
                .isNotEmpty().anyMatch((item) -> item.getId().equals(2L));

        assertThatExceptionOfType(ResourceNotFoundException.class)
        	.isThrownBy(() -> controllerA.deleteRelated(1, "dummyEntityBSet", new Object[] { -1, -2, 2 }))
                .withMessageContainingAll(String.format(RELATED_RESOURCE_NOT_FOUND_ERROR_FORMAT, DummyEntityB.class, "-1, -2"));
    }

    // endregion


    // region Method Sources

    private static Stream<Arguments> filters() {
        SpelExpressionParser expressionParser = new SpelExpressionParser();

        return Stream.of(
                Arguments.of(expressionParser.parseRaw("id==2"), 1),
                Arguments.of(expressionParser.parseRaw("id<0"), 0),
                // Arguments.of(expressionParser.parseRaw("id==-1"), 0),
                Arguments.of(null, 10));
    }

    private static Stream<Arguments> relatedResourceFilters() {
        SpelExpressionParser expressionParser = new SpelExpressionParser();

        return Stream.of(
                Arguments.of(1, expressionParser.parseRaw("id==2"), 1),
                Arguments.of(1, expressionParser.parseRaw("id<0"), 0),
                Arguments.of(1, null, 2));
    }

    private static Stream<Arguments> invalidIDSource() {
        return Stream.of(
                Arguments.of(Integer.class, "blah"),
                Arguments.of(UUID.class, "blah"));
    }

    private static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of(named("empty string", "")),
                Arguments.of(named("a string is not valid json", "example string")),
                Arguments.of(named("array not closed", "{ \"id\": [ \"string\" }")),
                Arguments.of(named("string not quoted", "{ \"id\": [ unquoted string ] }")),
                Arguments.of(named("back ticks not valid", "{ `id`: [ \"string\" ] }")),
                Arguments.of(named("no opening and closing braces with invalid property", "\"problem\": [ \"string\" ]"))
        );
    }

    private static Stream<Arguments> invalidProperty() {
        return Stream.of(
                Arguments.of(named("invalid property",  "{\"someProp\": \"someValue\"}")),
                Arguments.of(named("array not closed and property is invalid", "{ \"someProp\": [ \"string\" }")),
                Arguments.of(named("string not quoted and property is invalid", "{ \"someProp\": [ unquoted string ] }"))
        );
    }
    // endregion

    private <T, U> ResourceApiController<T, U> getResourceApiController(Class<T> clazz, Class<U> clazzU) {
        var entityUtils = new EntityUtils<T>(clazz, entityManager);
        return new ResourceApiController<T, U>(clazz, entityManager, transactionManager, entityUtils);
    }

}