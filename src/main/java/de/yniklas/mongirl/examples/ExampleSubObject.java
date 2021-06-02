package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "sub")
public class ExampleSubObject {
    @StoreWith(equalityRequirement = true)
    String haha = "muhaha";
}
