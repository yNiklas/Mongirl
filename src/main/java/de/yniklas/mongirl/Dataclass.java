package de.yniklas.mongirl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Dataclass {
    String collection();
    boolean addClasspath() default false;

    /**
     * Indicates that all attributes of the annotated class are relevant for the equality
     * check. If so, two objects of this class where all attributes are equal won't be stored separately.
     * This can cause problems if you don't sufficiently update the database via {@link Mongirl#store(Object)}.
     * For the safety (but disk-space heavy) way, set this to false.
     */
    boolean allAttributesEqualRelevant() default true;
}
