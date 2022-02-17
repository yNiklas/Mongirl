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
import de.yniklas.mongirl.exception.MongirlStoreException;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.lang.reflect.*;
import java.util.*;

/**
 * Mongirl let you store Java objects to a MongoDB and decodes them for you back to Java Objects.
 *
 * @author yNiklas
 */
public class Mongirl {
    private final MongoClient CLIENT;
    private final MongoDatabase DB;

    /**
     * With RAM_MODE enabled, {@link MongirlList}s don't hold all objects in RAM.
     * See {@link MongirlList} for more information.
     */
    public boolean ramMode = false;

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
                )))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();

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
                )))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();


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
        Object stored;

        try {
            stored = store(storageObject, storedObjects, postTasks);

            postTasks.forEach(task -> {
                if (createEqualityRequirementsSet(task.toStoreIn).size() != 0
                        && createEqualityRequirementsSet(task.value).size() != 0) {
                    Document saved = DB.getCollection(collection(task.toStoreIn.getClass()))
                            .find(Filters.and(createEqualityRequirementsSet(task.toStoreIn))).first();
                    Document foundTo = DB.getCollection(collection(task.value.getClass()))
                            .find(Filters.and(createEqualityRequirementsSet(task.value))).first();
                    Document foundFrom = new Document(saved);

                    if (foundTo != null) {
                        foundFrom.put(task.key, foundTo.getObjectId("_id"));
                    }

                    DB.getCollection(collection(task.toStoreIn.getClass())).findOneAndReplace(saved, foundFrom);
                }
            });
        } catch (Exception exception) {
            throw new MongirlStoreException(exception.getMessage());
        }

        return stored;
    }

    /**
     * Evaluates whether an object is stored based on its equal relevant attributes and, if so,
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

        if (collection(storageObject.getClass()) == null) {
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
        return decodeAll(targetClass, List.of());
    }

    public <T> List<T> decodeAll(Class<T> targetClass, Collection<ObjectId> blackList) {
        if (collection(targetClass) == null) {
            return null;
        }

        List<T> decodedObjects = new ArrayList<>();

        MongoCollection<Document> collection = DB.getCollection(collection(targetClass));
        for (Document document : collection.find()) {
            if (!blackList.contains(document.getObjectId("_id"))) {
                decodedObjects.add(decodeTo(targetClass, document.getObjectId("_id")));
            }
        }

        return decodedObjects;
    }

    /**
     * Decodes an object stored in the database with the given {@code ObjectId}.
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

    /**
     * Evaluates whether two objects are equal for Mongirl.
     * More precious, whether all equality requirement fields are equal.
     * areMongoEqual(null, null) will return true.
     *
     * @param a the first comparison object
     * @param b the second comparison object
     * @return whether both are equal for Mongirl
     */
    public static boolean areMongoEqual(Object a, Object b) {
        if (a == null) {
            return b == null;
        } else if (a.equals(b)) {
            return true;
        }

        List<Field> fieldsA = getFields(a);
        List<Field> fieldsB = getFields(b);

        if (fieldsA.size() != fieldsB.size()) {
            return false;
        }

        for (Field field : getFields(a)) {
            field.trySetAccessible();
            try {
                if (isEqualRelevant(field) && isMongoPrimitive(field.getType())) {
                    Object valueA = field.get(a);
                    Object valueB = field.get(b);

                    if ((valueA == null && valueB != null) || (valueA != null && valueB == null)) {
                        return false;
                    }

                    if (valueA != null) {
                        if (!valueA.equals(valueB)) {
                            return false;
                        }
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                return false;
            }
        }
        return true;
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

        Document updated = null;
        try {
            // Replace document, if found (updateOneAndReplace didn't work here!)
            updated = collection.findOneAndReplace(Filters.and(equalityRequirements), objAsDoc);
        } catch (CodecConfigurationException exception) {
            throw new MongirlStoreException(
                    String.format(
                            MongirlStoreException.NO_CODEC,
                            exception.getMessage()));
        }

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
                            ((List) field.get(storageObject)).forEach(item -> {
                                if (isMongoPrimitive(item.getClass())) {
                                    encoded.add(item);
                                } else {
                                    encoded.add(store(item, storedObjects, postTasks));
                                }
                            });
                            document.append(createStoreKey(field), encoded);
                        } else if (field.get(storageObject) instanceof Set) {
                            Set<Object> encoded = new HashSet<>();
                            ((Set) field.get(storageObject)).forEach(item -> {
                                if (isMongoPrimitive(item.getClass())) {
                                    encoded.add(item);
                                } else {
                                    encoded.add(store(item, storedObjects, postTasks));
                                }
                            });
                            document.append(createStoreKey(field), encoded);
                        }
                    } else if (field.getType().isArray()) {
                        List<Object> encoded = new ArrayList<>();
                        for (int i = 0; i < Array.getLength(field.get(storageObject)); i++) {
                            if (Array.get(field.get(storageObject), i) == null) {
                                encoded.add(null);
                            } else if (isMongoPrimitive(Array.get(field.get(storageObject), i).getClass())) {
                                encoded.add(Array.get(field.get(storageObject), i));
                            } else {
                                encoded.add(store(Array.get(field.get(storageObject), i)));
                            }
                        }
                        document.append(createStoreKey(field), encoded);
                    } else {
                        document.append(createStoreKey(field), store(field.get(storageObject), storedObjects, postTasks));
                    }
                } catch (IllegalAccessException exception) {
                    exception.printStackTrace();
                }
            }
        }

        // Store the classpath if annotated so or the class has a concrete super class or
        // implements an interface
        Store storeAnn = storageObject.getClass().getAnnotation(Store.class);
        Dataclass dataclassAnn = storageObject.getClass().getAnnotation(Dataclass.class);
        if ((storeAnn != null && storeAnn.addClasspath())
                || (dataclassAnn != null && dataclassAnn.addClasspath())
                || (storageObject.getClass().getSuperclass() != null && storageObject.getClass().getSuperclass() != Object.class)
                || (storageObject.getClass().getInterfaces().length != 0)) {
            document.append("classpath", storageObject.getClass().getName());
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

            // Specify constructor to use
            Constructor<T> targetConstructor = null;
            if (realClass.getConstructors().length == 0) {
                throw new MongirlDecodeException(String.format(MongirlDecodeException.NO_CONSTRICTOR, realClass.getName()));
            } else {
                targetConstructor = (Constructor<T>) realClass.getConstructors()[0];
            }

            // Create parameters list with default values
            List<Object> args = new LinkedList<>();
            for (Class<?> parameterType : realClass.getConstructors()[0].getParameterTypes()) {
                if (parameterType.equals(boolean.class)) {
                    args.add(false);
                } else if (parameterType.equals(byte.class) || parameterType.equals(short.class)
                        || parameterType.equals(int.class) || parameterType.equals(long.class)) {
                    args.add(0);
                } else if (parameterType.equals(float.class)) {
                    args.add(0.0f);
                } else if (parameterType.equals(double.class)) {
                    args.add(0.0);
                } else if (parameterType.equals(char.class)) {
                    args.add('\u0000');
                } else {
                    args.add(null);
                }
            }

            T emptyInstance = targetConstructor.newInstance(args.toArray());

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
            if (currentInspectionObject instanceof Integer) {
                field.set(emptyInstance, parseFromNumber((Integer) currentInspectionObject, field));
            } else {
                field.set(emptyInstance, currentInspectionObject);
            }
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
            if (field.getType().isArray()) {
                // Determine length to instantiate array
                Iterable<Object> dbEntry = (Iterable<Object>) currentInspectionObject;
                int length = 0;
                for (Object o : dbEntry) {
                    length++;
                }

                // Create array and copy content from the db entry
                Object arr = Array.newInstance(field.getType().getComponentType(), length);
                int i = 0;
                for (Object arrayItem : dbEntry) {
                    Array.set(arr, i++, parse(arrayItem, field.getType().getComponentType(), seenIds, decodedObjs, postTasks));
                }

                field.set(emptyInstance, arr);
            } else if (isClass(field.getType(), List.class)) {
                List<Object> list = new ArrayList<>();

                if (field.getGenericType() instanceof ParameterizedType) {
                    ((Iterable<Object>) currentInspectionObject).forEach(item -> {
                        list.add(parse(item,
                                (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0],
                                seenIds,
                                decodedObjs,
                                postTasks));
                    });
                }

                field.set(emptyInstance, list);
            } else if (isClass(field.getType(), Set.class)) {
                Set<Object> set = new HashSet<>();

                if (field.getGenericType() instanceof ParameterizedType) {
                    ((Iterable<Object>) currentInspectionObject).forEach(item -> {
                        set.add(parse(item,
                                (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0],
                                seenIds,
                                decodedObjs,
                                postTasks));
                    });
                }

                field.set(emptyInstance, set);
            }
        }
    }

    private Object parseFromNumber(Integer currentInspectionObject, Field field) {
        if (field.getType() == byte.class) {
            return currentInspectionObject.byteValue();
        } else if (field.getType() == short.class) {
            return currentInspectionObject.shortValue();
        } else if (field.getType() == long.class) {
            return currentInspectionObject.longValue();
        } else {
            return currentInspectionObject.intValue();
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
        } else if (isClass(inspection.getClass(), List.class)) {
            List<Object> list = new ArrayList<>();
            ((Iterable<Object>) inspection).forEach(item -> list.add(parse(item, item.getClass(), seenIds, decodedObjs, postTasks)));
            return list;
        } else if (isClass(inspection.getClass(), Set.class)) {
            Set<Object> set = new HashSet<>();
            ((Iterable<Object>) inspection).forEach(item -> set.add(parse(item, item.getClass(), seenIds, decodedObjs, postTasks)));
            return set;
        } else if (inspection.getClass().isArray()) {
            // todo: implement
        }
        return null;
    }

    private static boolean isMongoPrimitive(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || clazz.equals(Boolean.class)
                || clazz.equals(Byte.class)
                || clazz.equals(Short.class)
                || clazz.equals(Integer.class)
                || clazz.equals(Long.class)
                || clazz.equals(Float.class)
                || clazz.equals(Double.class)
                || clazz.equals(UUID.class);
    }

    private Set<Bson> createEqualityRequirementsSet(Object storageObject) {
        Set<Bson> equalityRequirements = new HashSet<>();
        for (Field field : getFields(storageObject)) {
            field.trySetAccessible();
            if (isEqualRelevant(field) && isMongoPrimitive(field.getType())) {
                try {
                    equalityRequirements.add(Filters.eq(createStoreKey(field), field.get(storageObject)));
                } catch (IllegalAccessException e) {
                    illegalAccess(e, field);
                }
            }
        }
        return equalityRequirements;
    }

    private static boolean isStored(Field field) {
        if (field.getAnnotation(DontStore.class) != null) {
            return false;
        }

        return field.getAnnotation(StoreWith.class) != null
                || field.getDeclaringClass().getAnnotation(Dataclass.class) != null;
    }

    static String collection(Class<?> clazz) {
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

    private static boolean isClass(Class<?> toCheck, Class<?> target) {
        if (toCheck == target) {
            return true;
        }

        Class<?> superClass = toCheck.getSuperclass();
        while (superClass != null) {
            if (superClass == target) {
                return true;
            }
        }

        return false;
    }

    MongoDatabase getDB() {
        return DB;
    }
}
