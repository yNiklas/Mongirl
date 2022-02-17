package de.yniklas.mongirl;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.util.*;

public class MongirlList<T> implements List<T> {
    /**
     * Object will only be hold in memory (RAM) if {@link Mongirl#ramMode} is enabled.
     * Otherwise, the add, remove and search operations will take place in the MongoDB directly.
     */
    private List<T> objectHold;
    private final Class<T> targetClass;
    private final Mongirl mongirl;

    private boolean ramMode;

    private final List<ObjectId> ramModeList;
    private boolean isRamModeListBlacklist;

    public MongirlList(Mongirl mongirl, Class<T> targetClass, boolean fromFullCollection) {
        this(mongirl, targetClass, fromFullCollection, false);
    }

    /**
     * Initializes a new MongirlList.
     *
     * @param mongirl the desired {@link Mongirl} to work with
     * @param targetClass the class of the Objects stored in the list
     * @param fromFullCollection when true, the whole collection is used as starting point
     *                           (so initial state is similar to {@link Mongirl#decodeAll}).
     *                           When false, the initial list is empty
     */
    public MongirlList(Mongirl mongirl, Class<T> targetClass, boolean fromFullCollection, boolean ramMode) {
        this.targetClass = targetClass;
        this.mongirl = mongirl;
        this.ramMode = ramMode;

        this.ramModeList = new LinkedList<>();

        if (fromFullCollection) {
            this.isRamModeListBlacklist = true;
        }

        if (!ramMode && fromFullCollection) {
            objectHold = mongirl.decodeAll(targetClass);
        } else {
            objectHold = new LinkedList<>();
        }
    }

    @Override
    public int size() {
        return size(false);
    }

    public int size(boolean estimated) {
        if (ramMode) {
            if (isRamModeListBlacklist) {
                if (Mongirl.collection(targetClass) == null) {
                    return 0;
                } else {
                    MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                    return (estimated ? (int) collection.estimatedDocumentCount() : (int) collection.countDocuments())
                            - ramModeList.size();
                }
            } else {
                return ramModeList.size();
            }
        } else {
            return objectHold.size();
        }
    }

    @Override
    public boolean isEmpty() {
        return size(true) == 0;
    }

    @Override
    public boolean contains(Object o) {
        if (ramMode) {
            ObjectId id = mongirl.getObjectIdFrom(o);
            if (id == null) {
                return false;
            }

            if (isRamModeListBlacklist) {
                return !ramModeList.contains(id);
            } else {
                return ramModeList.contains(id);
            }
        } else {
            for (T item : objectHold) {
                if (Mongirl.areMongoEqual(item, o)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Iterator<T> iterator() {
        if (!ramMode) {
            return objectHold.iterator();
        }

        return new Iterator<T>() {
            private boolean invalid = false;
            private int index = 0;
            private MongoCursor<Document> mongoIterator;

            @Override
            public boolean hasNext() {
                if (invalid) {
                    return false;
                }

                if (Mongirl.collection(targetClass) == null) {
                    invalid = true;
                    return false;
                }

                if (isRamModeListBlacklist) {
                    if (mongoIterator == null) {
                        MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                        mongoIterator = collection.find().iterator();
                    }

                    return mongoIterator.hasNext();
                } else {
                    return index < ramModeList.size();
                }
            }

            @Override
            public T next() {
                if (invalid) {
                    return null;
                }

                if (Mongirl.collection(targetClass) == null) {
                    invalid = true;
                    return null;
                }

                if (isRamModeListBlacklist) {
                    if (mongoIterator == null) {
                        MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                        mongoIterator = collection.find().iterator();
                    }

                    Document doc = mongoIterator.tryNext();
                    if (doc == null) {
                        return null;
                    }

                    index++;
                    return mongirl.decodeTo(targetClass, doc.getObjectId("_id"));
                } else {
                    index++;
                    return mongirl.decodeTo(targetClass, ramModeList.get(index-1));
                }
            }
        };
    }

    /**
     * CAUTION FOR RAM-MODE:
     * This method requires a <strong>full decoding</strong> of the corresponding database location.
     *
     * @return an array containing all decoded objects
     */
    @Override
    public Object[] toArray() {
        if (ramMode) {
            if (isRamModeListBlacklist) {
                if (Mongirl.collection(targetClass) == null) {
                    return new Object[0];
                }

                return mongirl.decodeAll(targetClass, ramModeList).toArray();
            } else {
                T[] decoded = (T[]) Array.newInstance(targetClass, ramModeList.size());
                for (int i = 0; i < ramModeList.size(); i++) {
                    decoded[i] = mongirl.decodeTo(targetClass, ramModeList.iterator().next());
                }
                return decoded;
            }
        } else {
            return objectHold.toArray();
        }
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        if (ramMode) {
            if (isRamModeListBlacklist) {
                if (Mongirl.collection(targetClass) == null) {
                    return null;
                }

                return mongirl.decodeAll(targetClass, ramModeList).toArray(a);
            } else {
                List<T> decoded = new LinkedList<>();
                ramModeList.forEach(objectId -> decoded.add(mongirl.decodeTo(targetClass, objectId)));
                return decoded.toArray(a);
            }

        } else {
            return objectHold.toArray(a);
        }
    }

    /**
     * Adds an element to the MongirlList if the list doesn't already contains it.
     * Note, that if the List operates in RAM mode, this operation does nothing.
     * Note, that this operation doesn't store the object.
     *
     * @param t the to be added object
     * @return whether the add operation succeeded
     */
    @Override
    public boolean add(T t) {
        if (contains(t)) {
            return true;
        }

        if (ramMode) {
            ObjectId id = mongirl.getObjectIdFrom(t);

            if (isRamModeListBlacklist) {
                // Remove from blacklist
                ramModeList.remove(id);
                return true;
            } else {
                // Add to specific list
                return ramModeList.add(id);
            }
        } else {
            objectHold.add(t);
            return true;
        }
    }

    /**
     * Removes an element from the MongirlList.
     * Note, that if the List operates in RAM mode, this operation does nothing.
     * Note, that this operation doesn't delete this object from the database.
     *
     * @param o the object to remove
     * @return whether the removal succeeded
     */
    @Override
    public boolean remove(Object o) {
        if (ramMode) {
            ObjectId id = mongirl.getObjectIdFrom(o);

            if (isRamModeListBlacklist) {
                // Add to blacklist
                return ramModeList.add(id);
            } else {
                // Remove from specific list
                return ramModeList.remove(id);
            }
        } else {
            for (T t : objectHold) {
                if (Mongirl.areMongoEqual(t, o)) {
                    return objectHold.remove(t);
                }
            }
            return false;
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (ramMode) {
            for (Object item : c) {
                if (!contains(item)) {
                    return false;
                }
            }
            return true;
        } else {
            return objectHold.containsAll(c);
        }
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T item : c) {
            add(item);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        int idxCounter = 0;
        for (T t : c) {
            add(index+idxCounter, t);
            idxCounter++;
        }
        return c.size() > 0;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (Object item : c) {
            if (!remove(item)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Clears the list but does not delete the DB entries itself.
     */
    @Override
    public void clear() {
        if (ramMode) {
            ramModeList.clear();
            if (isRamModeListBlacklist) {
                isRamModeListBlacklist = false;
            }
        } else {
            objectHold.clear();
        }
    }

    /**
     * Makes the list containing all elements in the collection.
     * Opposite to {@link MongirlList#clear}
     */
    public void addWholeCollection() {
        if (ramMode) {
            ramModeList.clear();
            if (!isRamModeListBlacklist) {
                isRamModeListBlacklist = true;
            }
        } else {
            objectHold = mongirl.decodeAll(targetClass);
        }
    }

    @Override
    public T get(int index) {
        if (ramMode) {
            if (index > size(false)) {
                return null;
            }

            if (isRamModeListBlacklist) {
                if (Mongirl.collection(targetClass) == null) {
                    return null;
                }

                MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                int idxCounter = 0;
                for (Document document : collection.find()) {
                    if (!ramModeList.contains(document.getObjectId("_id"))) {
                        if (idxCounter == index) {
                            return mongirl.decodeTo(targetClass, document.getObjectId("_id"));
                        } else {
                            idxCounter++;
                        }
                    }
                }
                return null;
            } else {
                return mongirl.decodeTo(targetClass, ramModeList.get(index));
            }
        } else {
            return objectHold.get(index);
        }
    }

    /**
     * Indexes on blacklist MongirlLists in RAM mode might aren't accurate.
     *
     * @param index the target index
     * @param element the to be set element
     * @return the original element at this index
     */
    @Override
    public T set(int index, T element) {
        if (ramMode) {
            if (isRamModeListBlacklist) {
                if (Mongirl.collection(targetClass) == null) {
                    return null;
                }

                MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                int idxCounter = 0;
                for (Document document : collection.find()) {
                    if (!ramModeList.contains(document.getObjectId("_id"))) {
                        if (idxCounter == index) {
                            T original = mongirl.decodeTo(targetClass, document.getObjectId("_id"));
                            remove(index);
                            add(index, element);
                            return original;
                        } else {
                            idxCounter++;
                        }
                    }
                }
                return null;
            } else {
                return mongirl.decodeTo(targetClass, ramModeList.set(index, mongirl.getObjectIdFrom(element)));
            }
        } else {
            return objectHold.set(index, element);
        }
    }

    /**
     * Indexes on MongirlLists with blacklists in RAM mode aren't implemented correctly.
     * In this case, the element is just added as in {@link MongirlList#add}.
     *
     * Note, that this method - similar to {@link MongirlList#add} - doesn't allow
     * a duplicate in the list. In the case that the element is already in, this method
     * will remove it before adding to the given index.
     *
     * @param index the target index
     * @param element the element to add
     */
    @Override
    public void add(int index, T element) {
        if (ramMode) {
            if (isRamModeListBlacklist) {
                add(element);
            } else {
                ObjectId id = mongirl.getObjectIdFrom(element);
                ramModeList.remove(id);
                ramModeList.add(index, mongirl.getObjectIdFrom(element));
            }
        } else {
            if (contains(element)) {
                remove(element);
            }
            objectHold.add(index, element);
        }
    }

    @Override
    public T remove(int index) {
        if (ramMode) {
            if (isRamModeListBlacklist) {
                if (Mongirl.collection(targetClass) == null) {
                    return null;
                }

                MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                int idxCounter = 0;
                for (Document document : collection.find()) {
                    if (!ramModeList.contains(document.getObjectId("_id"))) {
                        if (idxCounter == index) {
                            T original = mongirl.decodeTo(targetClass, document.getObjectId("_id"));
                            remove(original);
                            return original;
                        } else {
                            idxCounter++;
                        }
                    }
                }
                return null;
            } else {
                return mongirl.decodeTo(targetClass, ramModeList.remove(index));
            }
        } else {
            return objectHold.remove(index);
        }
    }

    @Override
    public int indexOf(Object o) {
        if (ramMode) {
            ObjectId id = mongirl.getObjectIdFrom(o);
            if (id == null) {
                return -1;
            }

            if (isRamModeListBlacklist) {
                if (ramModeList.contains(id)) {
                    return -1;
                }

                MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                int idx = 0;
                for (Document document : collection.find()) {
                    if (ramModeList.contains(document.getObjectId("_id"))) {
                        continue;
                    }

                    if (id.equals(document.getObjectId("_id"))) {
                        return idx;
                    }
                    idx++;
                }
                return -1;
            } else {
                return ramModeList.indexOf(id);
            }
        } else {
            int idx = 0;
            for (T t : objectHold) {
                if (Mongirl.areMongoEqual(t, o)) {
                    return idx;
                }
                idx++;
            }
            return -1;
        }
    }

    /**
     * As every element can only be once in the list, the method probably return the same
     * as {@link MongirlList#indexOf}.
     *
     * @param o the object to get the last occurrences index from
     * @return the index of the last occurrence of the given object
     */
    @Override
    public int lastIndexOf(Object o) {
        if (ramMode) {
            ObjectId id = mongirl.getObjectIdFrom(o);
            if (id == null) {
                return -1;
            }

            if (isRamModeListBlacklist) {
                if (ramModeList.contains(id)) {
                    return -1;
                }

                MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                int idx = 0;
                int lastIndex = -1;
                for (Document document : collection.find()) {
                    if (ramModeList.contains(document.getObjectId("_id"))) {
                        continue;
                    }

                    if (id.equals(document.getObjectId("_id"))) {
                        lastIndex = idx;
                    }
                    idx++;
                }
                return lastIndex;
            } else {
                return ramModeList.lastIndexOf(id);
            }
        } else {
            int idx = 0;
            int lastIndex = -1;
            for (T t : objectHold) {
                if (Mongirl.areMongoEqual(t, o)) {
                    lastIndex = idx;
                }
                idx++;
            }
            return lastIndex;
        }
    }

    @Override
    public ListIterator<T> listIterator() {
        return null;
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return null;
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        if (ramMode) {
            List<T> subList = new LinkedList<>();
            if (isRamModeListBlacklist) {
                MongoCollection<Document> collection = mongirl.getDB().getCollection(Mongirl.collection(targetClass));
                int idx = 0;
                for (Document document : collection.find()) {
                    if (!ramModeList.contains(document.getObjectId("_id"))) {
                        if (idx >= fromIndex && idx < toIndex) {
                            subList.add(mongirl.decodeTo(targetClass, document.getObjectId("_id")));
                        }
                        idx++;
                    }
                }
            } else {
                ramModeList.subList(fromIndex, toIndex).forEach(objectId -> subList.add(mongirl.decodeTo(targetClass, objectId)));
            }
            return subList;
        } else {
            return objectHold.subList(fromIndex, toIndex);
        }
    }
}
