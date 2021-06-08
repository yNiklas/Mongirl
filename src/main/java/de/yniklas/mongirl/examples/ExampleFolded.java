package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Store;
import de.yniklas.mongirl.StoreWith;

import java.util.List;

@Store(collection = "folded")
public class ExampleFolded {
    private static int id_counter = 12;
    @StoreWith(equalityRequirement = true) public String idd;

    @StoreWith public ExampleSubObject sub;

    @StoreWith public List<ExampleSubObject> subs;

    public ExampleFolded() {}

    public ExampleFolded(String subString) {
        this.idd = String.valueOf(id_counter);
        this.sub = new ExampleSubObject(subString);
        subs = List.of(new ExampleSubObject(subString), new ExampleSubObject(subString));

        id_counter++;
    }

    @Override
    public String toString() {
        return "ExampleFolded{" +
                "idd='" + idd + '\'' +
                ", sub=" + sub +
                ", subs=" + subs +
                '}';
    }
}
