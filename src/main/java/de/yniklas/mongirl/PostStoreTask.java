package de.yniklas.mongirl;

public class PostStoreTask {
    Object toStoreIn;
    String key;
    Object value;

    public PostStoreTask(Object toStoreIn, String key, Object value) {
        this.toStoreIn = toStoreIn;
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "PostStoreTask{" +
                "toStoreIn=" + toStoreIn +
                ", key='" + key + '\'' +
                ", value=" + value +
                '}';
    }
}
