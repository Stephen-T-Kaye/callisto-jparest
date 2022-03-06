package uk.gov.homeoffice.digital.sas.jparest.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.swagger.v3.oas.annotations.media.ExampleObject;
import uk.gov.homeoffice.digital.sas.jparest.controller.ResourceApiController;

/**
 * Specifies that this class is to be exposed as a resource by the {@link ResourceApiController}
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Resource {

    /**
     * (Optional) The path for the resource. Defaults to the lowercase unqualified
     * name of the entity class.
     */
    String path() default "";

    /**
     * An example of the filter than can be used to filter the resource.
     *
     * @return array of examples of the parameter
     **/
    ExampleObject[] filterExamples() default {};
}
