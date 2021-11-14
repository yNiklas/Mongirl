package de.yniklas.mongirl;

import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.codecs.DecoderContext;
import org.bson.types.ObjectId;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

@Deprecated
public class ConstructorPair {
    Class constructorParameterClass;
    Object value;

    public ConstructorPair(BsonReader reader, Field field, DecoderContext decoderContext) {
        if (field.getType().equals(ObjectId.class)) {

            constructorParameterClass = ObjectId.class;
            value = reader.readObjectId(Mongirl.createStoreKey(field));

        } else if (field.getType().equals(String.class)) {

            constructorParameterClass = String.class;
            value = reader.readString(Mongirl.createStoreKey(field));

        } else if (field.getType().equals(char.class) || field.getType().equals(Character.class)) {

            constructorParameterClass = Character.class;
            value = reader.readString(Mongirl.createStoreKey(field)).toCharArray()[0];

        } else if (field.getType().equals(Integer.class) || field.getType().equals(int.class)
                || field.getType().equals(short.class) || field.getType().equals(Short.class)) {

            constructorParameterClass = Integer.class;
            value = reader.readInt32(Mongirl.createStoreKey(field));

        } else if (field.getType().equals(Long.class) || field.getType().equals(long.class)) {

            constructorParameterClass = Long.class;
            value = reader.readInt64(Mongirl.createStoreKey(field));

        } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {

            constructorParameterClass = Boolean.class;
            value = reader.readBoolean(Mongirl.createStoreKey(field));

        } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)
                || field.getType().equals(float.class) || field.getType().equals(Float.class)) {

            constructorParameterClass = Double.class;
            value = reader.readDouble(Mongirl.createStoreKey(field));

        } else if (field.getType().isArray()) {
            reader.readStartArray();

            constructorParameterClass = field.getType();
            List<Object> arrayFields = new LinkedList<>();
            while (reader.readBsonType().equals(BsonType.END_OF_DOCUMENT)) {
                arrayFields.add(Mongirl.decode(field.getType().arrayType(), reader, decoderContext));
            }
            value = arrayFields.toArray();

            reader.readEndArray();
        } else if (field.getType().equals(List.class)) {
            reader.readStartArray();

            constructorParameterClass = field.getType();
            List<Object> listFields = new LinkedList<>();
            while (reader.readBsonType().equals(BsonType.END_OF_DOCUMENT)) {
                listFields.add(Mongirl.decode(field.getType().arrayType(), reader, decoderContext));
            }
            value = listFields;

            reader.readEndArray();
        }
    }

    public ConstructorPair(Class<?> clazz, Object value) {
        this.constructorParameterClass = clazz;
        this.value = value;
    }
}
