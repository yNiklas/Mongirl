package de.yniklas.mongirl;

import org.bson.types.ObjectId;

import java.lang.reflect.Field;

public class PostDecodeTask {
    Object toDecodeIn;
    Field toDefineAfterwards;
    ObjectId fill;

    public PostDecodeTask(Object toDecodeIn, Field toDefineAfterwards, ObjectId fill) {
        this.toDecodeIn = toDecodeIn;
        this.toDefineAfterwards = toDefineAfterwards;
        this.fill = fill;
    }
}
