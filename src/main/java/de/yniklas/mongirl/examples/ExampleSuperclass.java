package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Dataclass;

@Dataclass(collection = "superclass")
public class ExampleSuperclass {
    private String superSuperInt = "20";

    public String getSuperInt() {
        return superSuperInt;
    }
}
