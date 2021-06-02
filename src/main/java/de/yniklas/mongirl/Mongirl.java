package de.yniklas.mongirl;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;
import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Mongirl {
    private final MongoClient CLIENT;
    private final MongoDatabase DB;

    public Mongirl(String host, int port, String dbName) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(
                        new ServerAddress(host, port)
                ))).build();

        CLIENT = MongoClients.create(settings);
        DB = CLIENT.getDatabase(dbName);
    }

    public Mongirl(String host, int port, String dbName, String username, String authDB, char[] password) {
        MongoCredential credential = MongoCredential.createCredential(username, authDB, password);

        MongoClientSettings settings = MongoClientSettings.builder()
                .credential(credential)
                .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(
                        new ServerAddress(host, port)
                ))).build();


        CLIENT = MongoClients.create(settings);
        DB = CLIENT.getDatabase(dbName);
    }

    public Object store(Object storageObject) {
        if (storageObject.getClass().getAnnotation(Store.class) == null) {
            return null;
        }

        Document document = new Document();

        for (Field field : storageObject.getClass().getDeclaredFields()) {
            field.trySetAccessible();
            if (isStored(field)) {
                try {
                    if (field.get(storageObject) == null) {
                        document.append(createStoreKey(field), null);
                    } else if (isMongoPrimitive(field.get(storageObject).getClass())) {
                        document.append(createStoreKey(field), field.get(storageObject));
                    } else {
                        document.append(createStoreKey(field), store(field.get(storageObject)));
                    }
                } catch (IllegalAccessException ignored) { }
            }
        }

        Set<Bson> equalityRequirements = new HashSet<>();
        for (Field field : storageObject.getClass().getDeclaredFields()) {
            field.trySetAccessible();
            if (field.getAnnotation(StoreWith.class) != null
                    && field.getAnnotation(StoreWith.class).equalityRequirement()) {
                try {
                    equalityRequirements.add(Filters.eq(createStoreKey(field), field.get(storageObject)));
                } catch (IllegalAccessException e) {
                    illegalAccess(e, field);
                }
            }
        }

        MongoCollection<Document> collection = DB.getCollection(storageObject.getClass().getAnnotation(Store.class).collection());

        // Replace document, if found (updateOneAndReplace didn't work here!)
        Document updated = collection.findOneAndReplace(Filters.and(equalityRequirements), document);

        if (updated == null) {
            // Document wasn't replaced since there is no such document
            return collection.insertOne(document).getInsertedId();
        } else {
            return updated.getObjectId("_id");
        }
    }

    private boolean isMongoPrimitive(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || clazz.equals(Boolean.class)
                || clazz.equals(Byte.class)
                || clazz.equals(Short.class)
                || clazz.equals(Integer.class)
                || clazz.equals(Long.class)
                || clazz.equals(Float.class)
                || clazz.equals(Double.class)
                || clazz.equals(List.class)
                || clazz.equals(Set.class);
    }

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
        if (field.getAnnotation(DontStore.class) != null) {
            return false;
        }

        return field.getAnnotation(StoreWith.class) != null || field.getDeclaringClass().getAnnotation(Store.class) != null;
    }

    static String createStoreKey(Field field) {
        if (field.getAnnotation(StoreWith.class) == null) {
            return field.getName();
        } else {
            return field.getAnnotation(StoreWith.class).key().equals("") ? field.getName() : field.getAnnotation(StoreWith.class).key();
        }
    }

    private static void illegalAccess(IllegalAccessException e, Field field) {
        System.out.println("Couldn't access the field " + field + System.lineSeparator());
        e.printStackTrace();
    }
}
