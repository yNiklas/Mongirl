package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "collection")
public class ExampleStore {
    @StoreWith(key = "customId", equalityRequirement = true)
    public String superSuperIdentifier;

    int value = 5;

    @StoreWith(key = "x2")
    String x = "02052021/2";

    @StoreWith
    ExampleSubObject subsub = new ExampleSubObject();

    public ExampleStore(String id) {
        this.superSuperIdentifier = id;
    }
}
