package de.yniklas.mongirl.examples;

import de.yniklas.mongirl.Dataclass;

@Dataclass(collection = "dataclass")
public class ExampleDataclass {
    public String xStr;
    public int id;

    public ExampleDataclass(String xStr, int id) {
        this.xStr = xStr;
        this.id = id;
    }

    @Override
    public String toString() {
        return "ExampleDataclass{" +
                "xStr='" + xStr + '\'' +
                ", id=" + id +
                '}';
    }
}
