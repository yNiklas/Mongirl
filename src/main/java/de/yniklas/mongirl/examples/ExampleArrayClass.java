package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "array")
public class ExampleArrayClass {
    @StoreWith(equalityRequirement = true) String name = "otto";
    @StoreWith ExampleStore example = new ExampleStore("test");

    @StoreWith public int[] nmbrs = {1, 2};
    @StoreWith public ExampleSubObject[] enhancedArray;

    public ExampleArrayClass(int subArrLength) {
        enhancedArray = new ExampleSubObject[subArrLength];
        for (int i = 0; i < enhancedArray.length; i++) {
            enhancedArray[i] = new ExampleSubObject("testuser" + i);
        }
    }

    public ExampleArrayClass() {}
}
