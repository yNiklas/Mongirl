package de.yniklas.mongirl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface StoreWith {
    String key() default "";

    /**
     * Use case:
     * When the store operation is called, Mongirl will search the database
     * whether the object should be updated or store at first.
     * To check whether a stored database object is the object you want to store,
     * attributes with this boolean set will inspected.
     */
    boolean equalityRequirement() default false;

    /**
     * Indicates whether a field is part of the constructor.
     */
    boolean constructive() default false;
}
