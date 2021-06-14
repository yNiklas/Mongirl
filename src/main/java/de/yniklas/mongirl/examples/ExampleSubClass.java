package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Dataclass;

@Dataclass(collection = "subclass")
public class ExampleSubClass extends ExampleSuperclass {
    public String mySubString = "wooh";

    @Override
    public String toString() {
        return "ExampleSubClass{" +
                "mySubString='" + mySubString + '\'' + '}';
    }
}
