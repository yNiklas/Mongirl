package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

@Store(collection = "doubled")
public class ExampleDoubleConnection2 {
    @StoreWith(equalityRequirement = true) public int superID;
    @StoreWith public ExampleDoubleConnection1 connection1;

    public ExampleDoubleConnection2(ExampleDoubleConnection1 connection1) {
        superID = (int) (Math.random() * 1000);
        this.connection1 = connection1;
    }

    public ExampleDoubleConnection2() {
        superID = (int) (Math.random() * 1000);
    }

    @Override
    public String toString() {
        return "ExampleDoubleConnection2{" +
                "superID=" + superID +
                ", connection1=" + connection1.iddd +
                '}';
    }
}
