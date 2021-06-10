package de.yniklas.mongirl;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
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
        if (collection(storageObject.getClass()) == null) {
            return null;
        }

        Document objAsDoc = createDocumentOf(storageObject);

        // Collect all fields important for the equality check
        Set<Bson> equalityRequirements = createEqualityRequirementsSet(storageObject);

        MongoCollection<Document> collection = DB.getCollection(collection(storageObject.getClass()));

        if (equalityRequirements.size() == 0) {
            return collection.insertOne(objAsDoc).getInsertedId();
        }

        // Replace document, if found (updateOneAndReplace didn't work here!)
        Document updated = collection.findOneAndReplace(Filters.and(equalityRequirements), objAsDoc);

        if (updated == null) {
            // Document wasn't replaced since there is no such document
            return collection.insertOne(objAsDoc).getInsertedId();
        } else {
            return updated.getObjectId("_id");
        }
    }

    public ObjectId getObjectIdFrom(Object storageObject) {
        Document objAsDoc = createDocumentOf(storageObject);

        // Collect all fields important for the equality check
        Set<Bson> equalityRequirements = createEqualityRequirementsSet(storageObject);

        if (equalityRequirements.size() == 0) {
            return null;
        }

        MongoCollection<Document> collection = DB.getCollection(collection(storageObject.getClass()));
        Document foundDoc = collection.find(objAsDoc).first();

        return foundDoc != null ? foundDoc.getObjectId("_id") : null;
    }

    private Document createDocumentOf(Object storageObject) {
        Document document = new Document();

        for (Field field : storageObject.getClass().getDeclaredFields()) {
            field.trySetAccessible();
            if (isStored(field)) {
                try {
                    if (field.get(storageObject) == null) {
                        document.append(createStoreKey(field), null);
                    } else if (isMongoPrimitive(field.get(storageObject).getClass())) {
                        document.append(createStoreKey(field), field.get(storageObject));
                    } else if (field.isEnumConstant()) {
                        document.append(createStoreKey(field), field.get(storageObject).toString());
                    } else if (field.get(storageObject) instanceof Iterable) {
                        if (field.get(storageObject) instanceof List) {
                            List<Object> encoded = new ArrayList<>();
                            ((List) field.get(storageObject)).forEach(item -> encoded.add(store(item)));
                            document.append(createStoreKey(field), encoded);
                        } else if (field.get(storageObject) instanceof Set) {
                            Set<Object> encoded = new HashSet<>();
                            ((Set) field.get(storageObject)).forEach(item -> encoded.add(store(item)));
                            document.append(createStoreKey(field), encoded);
                        }
                    } else {
                        document.append(createStoreKey(field), createDocumentOf(field.get(storageObject)).getObjectId("_id"));
                    }
                } catch (IllegalAccessException exception) {
                    exception.printStackTrace();
                }
            }
        }

        return document;
    }

    public <T> T decodeFromFilters(Class<T> targetClass, Pair... pairs) {
        if (collection(targetClass) == null) {
            return null;
        }

        if (pairs.length == 0) {
            return decodeAll(targetClass).get(0);
        }

        Set<Bson> filters = new HashSet<>();
        for (Pair pair : pairs) {
            if (isMongoPrimitive(pair.value.getClass()) || pair.value == null) {
                filters.add(Filters.eq(pair.key, pair.value));
            } else {
                ObjectId subObjId = getObjectIdFrom(pair.value);
                if (subObjId == null) {
                    // Object isn't present in the database so cannot be the reference to the key
                    return null;
                } else {
                    filters.add(Filters.eq(pair.key, subObjId));
                }
            }
        }

        Document foundDocument = DB.getCollection(collection(targetClass)).find(Filters.and(filters)).first();
        if (foundDocument == null) {
            return null;
        }

        return decodeTo(targetClass, foundDocument.getObjectId("_id"));
    }

    public <T> List<T> decodeAll(Class<T> targetClass) {
        if (collection(targetClass) == null) {
            return null;
        }

        List<T> decodedObjects = new ArrayList<>();

        MongoCollection<Document> collection = DB.getCollection(collection(targetClass));
        for (Document document : collection.find()) {
            try {
                T emptyInstance = targetClass.getConstructor().newInstance();

                for (Field field : targetClass.getDeclaredFields()) {
                    field.trySetAccessible();
                    if (isStored(field)) {
                        defineFieldValue(document, emptyInstance, field);
                    }
                }

                decodedObjects.add(emptyInstance);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        return decodedObjects;
    }

    private <T> void defineFieldValue(Document document, T emptyInstance, Field field) throws IllegalAccessException {
        Object currentInspectionObject = document.get(createStoreKey(field));

        if (isMongoPrimitive(field.getType())) {
            field.set(emptyInstance, currentInspectionObject);
        } else if (currentInspectionObject instanceof ObjectId) {
            field.set(emptyInstance, decodeTo(field.getType(), (ObjectId) currentInspectionObject));
        } else if (currentInspectionObject instanceof Iterable) {
            // List/Set/Array handling
            if (currentInspectionObject instanceof List) {
                List<Object> list = new ArrayList<>();

                if (field.getGenericType() instanceof ParameterizedType) {
                    ((List<Object>) currentInspectionObject).forEach(item -> {
                        list.add(parse(item, (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]));
                    });
                }

                field.set(emptyInstance, list);
            } else if (currentInspectionObject instanceof Set) {
                Set<Object> set = new HashSet<>();

                if (field.getGenericType() instanceof ParameterizedType) {
                    ((List<Object>) currentInspectionObject).forEach(item -> {
                        set.add(parse(item, (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]));
                    });
                }

                field.set(emptyInstance, set);
            } else if (currentInspectionObject.getClass().isArray()) {
                // todo: implement
            }
        }
    }

    public <T> T decodeTo(Class<T> targetClass, ObjectId _id) {
        if (collection(targetClass) == null) {
            return null;
        }

        MongoCollection<Document> collection = DB.getCollection(collection(targetClass));
        Document foundDocument = collection.find(Filters.eq("_id", _id)).first();

        if (foundDocument == null) {
            return null;
        }

        try {
            T emptyInstance = targetClass.getConstructor().newInstance();

            // Reflect all stored attributes
            for (Field field : targetClass.getDeclaredFields()) {
                field.trySetAccessible();
                if (isStored(field)) {
                    defineFieldValue(foundDocument, emptyInstance, field);
                }
            }

            return emptyInstance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Object parse(Object inspection, Class<?> genericClass) {
        if (isMongoPrimitive(inspection.getClass())) {
            return inspection;
        } else if (inspection instanceof ObjectId) {
            return decodeTo(genericClass, (ObjectId) inspection);
        } else if (inspection instanceof List) {
            // todo: implement
        } else if (inspection instanceof Set) {
            // todo: implement
        } else if (inspection.getClass().isArray()) {
            // todo: implement
        }
        return null;
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
                || clazz.equals(Double.class);
    }

    private static Set<Bson> createEqualityRequirementsSet(Object storageObject) {
        Set<Bson> equalityRequirements = new HashSet<>();
        for (Field field : storageObject.getClass().getDeclaredFields()) {
            field.trySetAccessible();
            if (isEqualRelevant(field)) {
                try {
                    equalityRequirements.add(Filters.eq(createStoreKey(field), field.get(storageObject)));
                } catch (IllegalAccessException e) {
                    illegalAccess(e, field);
                }
            }
        }
        return equalityRequirements;
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

        return field.getAnnotation(StoreWith.class) != null
                || field.getDeclaringClass().getAnnotation(Dataclass.class) != null;
    }

    private static String collection(Class<?> clazz) {
        if (clazz.getAnnotation(Store.class) == null
                && clazz.getAnnotation(Dataclass.class) == null) {
            return null;
        }

        if (clazz.getAnnotation(Store.class) != null) {
            return clazz.getAnnotation(Store.class).collection();
        } else {
            return clazz.getAnnotation(Dataclass.class).collection();
        }
    }

    private static boolean isEqualRelevant(Field field) {
        if (!isStored(field)) {
            return false;
        }

        return (field.getAnnotation(StoreWith.class) != null
                && field.getAnnotation(StoreWith.class).equalityRequirement())
                || field.getAnnotation(Dataclass.class) != null;
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
