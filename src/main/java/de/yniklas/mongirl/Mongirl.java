package de.yniklas.mongirl;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import de.yniklas.mongirl.exception.MongirlDecodeException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

/**
 * Mongirl let you store Java objects to a MongoDB and decodes them for you back to Java Objects.
 *
 * @author yNiklas
 */
public class Mongirl {
    private final MongoClient CLIENT;
    private final MongoDatabase DB;

    /**
     * Creates a {@code Mongirl} instance without any credentials or authentication.
     *
     * @param host the host address of the database
     * @param port the port of the database
     * @param dbName the name of the database
     */
    public Mongirl(String host, int port, String dbName) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(
                        new ServerAddress(host, port)
                ))).build();

        CLIENT = MongoClients.create(settings);
        DB = CLIENT.getDatabase(dbName);
    }

    /**
     * Create a {@code Mongirl} instance with credentials for the target database.
     *
     * @param host the host address of the database
     * @param port the port of the database
     * @param dbName the name of the database
     * @param username the credentials username to authenticate the database connection
     * @param authDB the database auth
     * @param password the credentials password to authenticate the database connection
     */
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

    /**
     * Stores the given object to the database.
     * An attribute of the object will be stored iff
     *
     * <ul>
     *     <li>The attribute is annotated with {@link StoreWith}</li>
     *     <li>The class to the object is annotated with {@link Store}</li>
     *     <li>The attribute isn't annotated with {@link DontStore}</li>
     * </ul>
     * or
     * <ul>
     *     <li>The class to the object is annotated with {@link Dataclass}</li>
     *     <li>The attribute isn't annotated with {@link DontStore}</li>
     * </ul>
     *
     * @param storageObject the object to store
     * @return the ObjectId of the stored object or the inserted Id
     */
    public Object store(Object storageObject) {
        List<Object> storedObjects = new ArrayList<>();
        List<PostStoreTask> postTasks = new ArrayList<>();

        Object stored = store(storageObject, storedObjects, postTasks);

        postTasks.forEach(task -> {
            Document saved = DB.getCollection(collection(task.toStoreIn.getClass()))
                    .find(Filters.and(createEqualityRequirementsSet(task.toStoreIn))).first();
            Document foundTo = DB.getCollection(collection(task.value.getClass()))
                    .find(Filters.and(createEqualityRequirementsSet(task.value))).first();

            Document foundFrom = new Document(saved);

            if (foundTo != null) {
                foundFrom.put(task.key, foundTo.getObjectId("_id"));
            }

            DB.getCollection(collection(task.toStoreIn.getClass())).findOneAndReplace(saved, foundFrom);
        });

        return stored;
    }

    /**
     * Evaluates whether a object is stored based on its equal relevant attributes and, if so,
     * returns its {@code ObjectId}.
     *
     * @param storageObject the object to get the {@code ObjectId} from
     * @return the objects {@code ObjectId} if exists
     */
    public ObjectId getObjectIdFrom(Object storageObject) {
        // Collect all fields important for the equality check
        Set<Bson> equalityRequirements = createEqualityRequirementsSet(storageObject);

        if (equalityRequirements.size() == 0) {
            return null;
        }

        MongoCollection<Document> collection = DB.getCollection(collection(storageObject.getClass()));
        Document foundDoc = collection.find(Filters.and(equalityRequirements)).first();

        return foundDoc != null ? foundDoc.getObjectId("_id") : null;
    }

    /**
     * Decodes a database stored object based on given filters and the class of the to be decoded
     * object.
     *
     * @param targetClass the {@code Class} of the decoded object
     * @param pairs the search parameters given as {@link Pair}
     * @param <T> the type of the decoded object
     * @return the decoded, first result of the database search with the given {@code pairs} or
     *         null if there was no search results
     */
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

    /**
     * Decodes all objects of a given type stored in the database.
     *
     * @param targetClass the {@code Class} of the objects to decode
     * @param <T> the type of the decoded objects
     * @return a {@link List} with all decoded objects
     */
    public <T> List<T> decodeAll(Class<T> targetClass) {
        if (collection(targetClass) == null) {
            return null;
        }

        List<T> decodedObjects = new ArrayList<>();

        MongoCollection<Document> collection = DB.getCollection(collection(targetClass));
        for (Document document : collection.find()) {
            decodedObjects.add(decodeTo(targetClass, document.getObjectId("_id")));
        }

        return decodedObjects;
    }

    /**
     * Decodes a object stored in the database with the given {@code ObjectId}.
     *
     * @param targetClass the {@code Class} of the decoded object
     * @param _id the {@code ObjectId} of the database document to decode
     * @param <T> the type of the decoded object
     * @return the decoded object or null if the decode fail
     */
    public <T> T decodeTo(Class<T> targetClass, ObjectId _id) {
        List<ObjectId> seenObjectIds = new ArrayList<>();
        Hashtable<ObjectId, Object> decodedObjects = new Hashtable<>();
        List<PostDecodeTask> postTasks = new ArrayList<>();

        T decoded = decodeTo(targetClass, _id, seenObjectIds, decodedObjects, postTasks);

        postTasks.forEach(task -> {
            try {
                task.toDefineAfterwards.set(task.toDecodeIn, decodedObjects.get(task.fill));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });

        return decoded;
    }

    private Object store(Object storageObject, List<Object> alreadyStored, List<PostStoreTask> postTasks) {
        if (collection(storageObject.getClass()) == null) {
            return null;
        }

        Document objAsDoc = createDocumentOf(storageObject, alreadyStored, postTasks);

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

    private <T> T decodeTo(Class<T> targetClass,
                          ObjectId _id,
                          List<ObjectId> seenIds,
                          Hashtable<ObjectId, Object> decodedObjs,
                          List<PostDecodeTask> postTasks) {
        seenIds.add(_id);

        if (collection(targetClass) == null) {
            return null;
        }

        MongoCollection<Document> collection = DB.getCollection(collection(targetClass));
        Document foundDocument = collection.find(Filters.eq("_id", _id)).first();

        if (foundDocument == null) {
            return null;
        }

        T createdObj = create(targetClass, foundDocument, seenIds, decodedObjs, postTasks);
        decodedObjs.put(_id, createdObj);
        return createdObj;
    }

    private Document createDocumentOf(Object storageObject, List<Object> storedObjects, List<PostStoreTask> postTasks) {
        storedObjects.add(storageObject);
        Document document = new Document();

        for (Field field : getFields(storageObject)) {
            field.trySetAccessible();
            if (isStored(field)) {
                try {
                    if (storedObjects.contains(field.get(storageObject))) {
                        postTasks.add(new PostStoreTask(storageObject, createStoreKey(field), field.get(storageObject)));
                    } else if (field.get(storageObject) == null) {
                        document.append(createStoreKey(field), null);
                    } else if (isMongoPrimitive(field.get(storageObject).getClass())) {
                        document.append(createStoreKey(field), field.get(storageObject));
                    } else if (field.getType().isEnum()) {
                        document.append(createStoreKey(field), field.get(storageObject).toString());
                    } else if (field.get(storageObject) instanceof Iterable) {
                        if (field.get(storageObject) instanceof List) {
                            List<Object> encoded = new ArrayList<>();
                            ((List) field.get(storageObject)).forEach(item -> encoded.add(store(item, storedObjects, postTasks)));
                            document.append(createStoreKey(field), encoded);
                        } else if (field.get(storageObject) instanceof Set) {
                            Set<Object> encoded = new HashSet<>();
                            ((Set) field.get(storageObject)).forEach(item -> encoded.add(store(item, storedObjects, postTasks)));
                            document.append(createStoreKey(field), encoded);
                        }
                    } else {
                        document.append(createStoreKey(field), store(field.get(storageObject), storedObjects, postTasks));
                    }
                } catch (IllegalAccessException exception) {
                    exception.printStackTrace();
                }
            }
        }

        Store storeAnn = storageObject.getClass().getAnnotation(Store.class);
        if (storeAnn != null && storeAnn.addClasspath()) {
            document.append("classpath", storageObject.getClass().getName());
        } else {
            Dataclass dataclassAnn = storageObject.getClass().getAnnotation(Dataclass.class);
            if (dataclassAnn != null && dataclassAnn.addClasspath()) {
                document.append("classpath", storageObject.getClass().getName());
            }
        }

        return document;
    }

    private <T> T create(Class<T> targetClass,
                         Document document,
                         List<ObjectId> seenIds,
                         Hashtable<ObjectId, Object> decodedObjs,
                         List<PostDecodeTask> postTasks) {
        Class<T> realClass = targetClass;
        try {
            if (document.get("classpath") != null) {
                realClass = (Class<T>) Class.forName((String) document.get("classpath"));
            }

            T emptyInstance = realClass.getConstructor().newInstance();

            // Reflect all stored attributes
            for (Field field : getFields(realClass)) {
                field.trySetAccessible();
                if (isStored(field)) {
                    defineFieldValue(document, emptyInstance, field, seenIds, decodedObjs, postTasks);
                }
            }

            return emptyInstance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchMethodException e) {
            throw new MongirlDecodeException(String.format(MongirlDecodeException.NO_DEFAULT_CONSTRICTOR, realClass.getName()));
        }
    }

    private <T> void defineFieldValue(Document document,
                                      T emptyInstance,
                                      Field field,
                                      List<ObjectId> seenIds,
                                      Hashtable<ObjectId, Object> decodedObjs,
                                      List<PostDecodeTask> postTasks) throws IllegalAccessException {
        Object currentInspectionObject = document.get(createStoreKey(field));

        if (isMongoPrimitive(field.getType())) {
            field.set(emptyInstance, currentInspectionObject);
        } else if (currentInspectionObject instanceof ObjectId) {
            if (seenIds.contains((ObjectId) currentInspectionObject)) {
                postTasks.add(new PostDecodeTask(emptyInstance, field, (ObjectId) currentInspectionObject));
            } else {
                field.set(emptyInstance, decodeTo(field.getType(), (ObjectId) currentInspectionObject, seenIds, decodedObjs, postTasks));
            }
        } else if (field.getType().isEnum()) {
            for (Object enumConstant : field.getType().getEnumConstants()) {
                if (enumConstant.toString().equals(currentInspectionObject)) {
                    field.set(emptyInstance, enumConstant);
                    break;
                }
            }
        } else if (currentInspectionObject instanceof Iterable) {
            // List/Set/Array handling
            if (currentInspectionObject instanceof List) {
                List<Object> list = new ArrayList<>();

                if (field.getGenericType() instanceof ParameterizedType) {
                    ((List<Object>) currentInspectionObject).forEach(item -> {
                        list.add(parse(item,
                                (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0],
                                seenIds,
                                decodedObjs,
                                postTasks));
                    });
                }

                field.set(emptyInstance, list);
            } else if (currentInspectionObject instanceof Set) {
                Set<Object> set = new HashSet<>();

                if (field.getGenericType() instanceof ParameterizedType) {
                    ((List<Object>) currentInspectionObject).forEach(item -> {
                        set.add(parse(item,
                                (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0],
                                seenIds,
                                decodedObjs,
                                postTasks));
                    });
                }

                field.set(emptyInstance, set);
            } else if (currentInspectionObject.getClass().isArray()) {
                // todo: implement
            }
        }
    }

    private Object parse(Object inspection,
                         Class<?> genericClass,
                         List<ObjectId> seenIds,
                         Hashtable<ObjectId, Object> decodedObjs,
                         List<PostDecodeTask> postTasks) {
        if (isMongoPrimitive(inspection.getClass())) {
            return inspection;
        } else if (inspection instanceof ObjectId) {
            return decodeTo(genericClass, (ObjectId) inspection, seenIds, decodedObjs, postTasks);
        } else if (inspection instanceof List) {
            List<Object> list = new ArrayList<>();
            ((List<Object>) inspection).forEach(item -> list.add(parse(item, item.getClass(), seenIds, decodedObjs, postTasks)));
            return list;
        } else if (inspection instanceof Set) {
            Set<Object> set = new HashSet<>();
            ((Set<Object>) inspection).forEach(item -> set.add(parse(item, item.getClass(), seenIds, decodedObjs, postTasks)));
            return set;
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

    private Set<Bson> createEqualityRequirementsSet(Object storageObject) {
        Set<Bson> equalityRequirements = new HashSet<>();
        for (Field field : getFields(storageObject)) {
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

        for (Field field : getFields(toEncode)) {
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
        for (Field field : getFields(toDecode)) {
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

    private boolean isEqualRelevant(Field field) {
        if (!isStored(field)) {
            return false;
        }

        return (field.getAnnotation(StoreWith.class) != null
                && field.getAnnotation(StoreWith.class).equalityRequirement())
                || (field.getDeclaringClass().getAnnotation(Dataclass.class) != null
                && field.getDeclaringClass().getAnnotation(Dataclass.class).allAttributesEqualRelevant());
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

    private static <T> List<Field> getFields(T t) {
        return getFields(t.getClass());
    }

    private static <T> List<Field> getFields(Class<T> targetClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = targetClass;

        while (clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
