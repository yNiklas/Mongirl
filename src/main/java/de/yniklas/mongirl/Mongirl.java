package de.yniklas.mongirl;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class Mongirl {
    public static void encode(Object toEncode, BsonWriter writer, EncoderContext encoderContext) {
        writer.writeStartDocument();

        for (Field field : toEncode.getClass().getDeclaredFields()) {
            if (isStored(field)) {
                try {
                    if (field.getType().equals(ObjectId.class)) {
                        writer.writeObjectId(createStoreKey(field), (ObjectId) field.get(toEncode));
                    } else if (field.getType().equals(String.class)) {
                        writer.writeString(createStoreKey(field), (String) field.get(toEncode));
                    } else if (field.getType().equals(Integer.class) || field.getType().equals(int.class)
                            || field.getType().equals(short.class) || field.getType().equals(Short.class)) {
                        writer.writeInt32(createStoreKey(field), (Integer) field.get(toEncode));
                    } else if (field.getType().equals(Long.class) || field.getType().equals(long.class)) {
                        writer.writeInt64(createStoreKey(field), (Long) field.get(toEncode));
                    } else if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                        writer.writeBoolean(createStoreKey(field), (Boolean) field.get(toEncode));
                    } else if (field.getType().equals(double.class) || field.getType().equals(Double.class)
                            || field.getType().equals(float.class) || field.getType().equals(Float.class)) {
                        writer.writeDouble(createStoreKey(field), (Double) field.get(toEncode));
                    } else if (field.getType().equals(char.class) || field.getType().equals(Character.class)) {
                        writer.writeString(createStoreKey(field), String.valueOf(field.get(toEncode)));
                    } else if (field.getType().isArray()) {
                        writer.writeStartArray();
                        for (int i = 0; i < Array.getLength(field.get(toEncode)); i++) {
                            encode(Array.get(field.get(toEncode), i), writer, encoderContext);
                        }
                        writer.writeEndArray();
                    } else if (field.getType().equals(List.class)) {
                        writer.writeStartArray();
                        ((List) field.get(toEncode)).forEach(item -> {
                            encode(item, writer, encoderContext);
                        });
                        writer.writeEndArray();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        writer.writeEndDocument();
    }

    public static <T> T decode(Class<T> toDecode, BsonReader reader, DecoderContext decoderContext) {
        reader.readStartDocument();

        ConstructorList constructorList = new ConstructorList();
        for (Field field : toDecode.getDeclaredFields()) {
            constructorList.addPair(new ConstructorPair(reader, field, decoderContext));
        }
        reader.readEndDocument();

        try {
            return toDecode.getConstructor(constructorList.getConstructorClasses()).newInstance((Object) constructorList.getConstructObjects());
        } catch (InvocationTargetException | InstantiationException
                | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static boolean isStored(Field field) {
        return field.getAnnotation(Store.class) != null;
    }

    static String createStoreKey(Field field) {
        return field.getAnnotation(Store.class).key().equals("") ? field.getName() : field.getAnnotation(Store.class).key();
    }
}
