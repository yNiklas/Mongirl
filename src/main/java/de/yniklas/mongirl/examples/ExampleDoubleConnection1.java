package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

import java.util.List;

@Store(collection = "doubledorigin")
public class ExampleDoubleConnection1 {
    @StoreWith(equalityRequirement = true) public int iddd;
    @StoreWith public List<ExampleDoubleConnection2> connection2s;

    public ExampleDoubleConnection1() {
        iddd = (int) (Math.random() * 1000);
        connection2s = List.of(new ExampleDoubleConnection2(this));
    }

    @Override
    public String toString() {
        return "ExampleDoubleConnection1{" +
                "iddd=" + iddd +
                ", connection2s=" + connection2s +
                '}';
    }
}
