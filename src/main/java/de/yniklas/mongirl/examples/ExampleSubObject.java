package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "sub")
public class ExampleSubObject {
    @StoreWith(equalityRequirement = true)
    public String haha = "muhaha2";

    public ExampleSubObject() {
    }

    public ExampleSubObject(String haha) {
        this.haha = haha;
    }

    @Override
    public String toString() {
        return "ExampleSubObject{" +
                "haha='" + haha + '\'' +
                '}';
    }
}
