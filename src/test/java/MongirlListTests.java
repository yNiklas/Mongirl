import de.yniklas.mongirl.MongirlList;
import de.yniklas.mongirl.examples.ExampleStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MongirlListTests {
    @BeforeAll
    public static void initDB() {
        MongirlTests.initDB();
    }

    @Test
    public void testAdd() {
        MongirlList<ExampleStore> list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, true);
        int currentSize = list.size();
        ExampleStore newEntry = new ExampleStore("testAddtt");
        MongirlTests.testMongirl.store(newEntry);
        list.add(newEntry);
        assertEquals(currentSize + 1, list.size());
        assertEquals("testAddtt", list.get(list.size() - 1).superSuperIdentifier);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, false);
        currentSize = list.size();
        newEntry = new ExampleStore("testAddtf");
        MongirlTests.testMongirl.store(newEntry);
        list.add(newEntry);
        assertEquals(currentSize + 1, list.size());
        assertEquals("testAddtf", list.get(list.size() - 1).superSuperIdentifier);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, false, true);
        newEntry = new ExampleStore("testAddft");
        MongirlTests.testMongirl.store(newEntry);
        list.add(newEntry);
        assertEquals(1, list.size());
        assertEquals("testAddft", list.get(0).superSuperIdentifier);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, false, false);
        newEntry = new ExampleStore("testAddff");
        MongirlTests.testMongirl.store(newEntry);
        list.add(newEntry);
        assertEquals(1, list.size());
        assertEquals("testAddff", list.get(0).superSuperIdentifier);
    }

    @Test
    public void testIterators() {
        for (int i = 0; i < 5; i++) {
            MongirlTests.testMongirl.store(new ExampleStore(String.valueOf(i)));
        }

        MongirlList<ExampleStore> list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, true);
        testIteratorsWith(list);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, false);
        testIteratorsWith(list);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, false, true);
        for (int i = 0; i < 5; i++) {
            ExampleStore t = new ExampleStore(String.valueOf(i));
            list.add(t);
            MongirlTests.testMongirl.store(t);
        }
        testIteratorsWith(list);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, false, false);
        for (int i = 0; i < 5; i++) {
            ExampleStore t = new ExampleStore(String.valueOf(i));
            list.add(t);
            MongirlTests.testMongirl.store(t);
        }
        testIteratorsWith(list);
    }

    private void testIteratorsWith(MongirlList<ExampleStore> list) {
        int id = 0;
        for (ExampleStore exampleStore : list) {
            assertEquals(id, Integer.parseInt(exampleStore.superSuperIdentifier));
            id++;
        }

        Iterator<ExampleStore> it = list.iterator();
        for (int i = 0; i < list.size(); i++) {
            assertEquals(i, Integer.parseInt(it.next().superSuperIdentifier));
        }

        var ref = new Object() {
            int idx = 0;
        };
        list.forEach(exampleStore -> {
            assertEquals(ref.idx, Integer.parseInt(exampleStore.superSuperIdentifier));
            ref.idx++;
        });
    }

    @BeforeEach
    public void cleanDB() {
        clearUpDB();
    }

    @AfterAll
    public static void clearUpDB() {
        MongirlTests.DB.drop();
    }
}
