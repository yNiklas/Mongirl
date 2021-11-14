package de.yniklas.mongirl;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class ConstructorList {
    private final List<ConstructorPair> pairs;

    public ConstructorList() {
        this.pairs = new ArrayList<>();
    }

    public void addPair(ConstructorPair pair) {
        this.pairs.add(pair);
    }

    public Class<?>[] getConstructorClasses() {
        Class<?>[] classes = new Class[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            classes[i] = pairs.get(i).constructorParameterClass;
        }
        return classes;
    }

    public Object[] getConstructObjects() {
        Object[] values = new Object[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            values[i] = pairs.get(i).value;
        }
        return values;
    }
}
