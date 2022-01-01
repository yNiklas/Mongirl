package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "enum")
public class ExampleEnum {
    @StoreWith(equalityRequirement = true) public int id = 6;
    @StoreWith public ExampleEnumEnum tip = ExampleEnumEnum.TYPO2;

    public ExampleEnum(int id) {
        this.id = id;
    }
}
