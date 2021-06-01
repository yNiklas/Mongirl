package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "collection")
public class ExampleStore {
    @StoreWith(key = "customId", equalityRequirement = true)
    private String superSuperIdentifier;

    int value = 5;

    @StoreWith(key = "x2")
    String x = "2";

    ExampleSubObject subsub = new ExampleSubObject();

    public ExampleStore(String id) {
        this.superSuperIdentifier = id;
    }
}
