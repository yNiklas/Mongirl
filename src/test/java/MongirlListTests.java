import de.yniklas.mongirl.MongirlList;
import de.yniklas.mongirl.examples.ExampleStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void testRemove() {
        for (int i = 0; i < 4; i++) {
            MongirlTests.testMongirl.store(new ExampleStore(String.valueOf(i)));
        }
        ExampleStore es = new ExampleStore("4");
        MongirlTests.testMongirl.store(es);

        MongirlList<ExampleStore> list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, true);
        int currentSize = list.size();
        list.remove(es);
        assertEquals(currentSize - 1, list.size());
        assertEquals("3", list.get(list.size() - 1).superSuperIdentifier);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, false);
        currentSize = list.size();
        assertTrue(list.contains(es));
        list.remove(es);
        assertEquals(currentSize - 1, list.size());
        assertEquals("3", list.get(list.size() - 1).superSuperIdentifier);

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, false, true);
        list.add(es);
        assertEquals(1, list.size());
        list.remove(es);
        assertEquals(0, list.size());

        list = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, false, false);
        list.add(es);
        assertEquals(1, list.size());
        list.remove(es);
        assertEquals(0, list.size());
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

    @Test
    public void testPerformance() {
        for (int i = 0; i < 2000; i++) {
            MongirlTests.testMongirl.store(new ExampleStore(String.valueOf(i)));
        }

        long memInitial, startTime;

        memInitial = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        MongirlList<ExampleStore> fastList = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, false);
        long memFastList = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - memInitial;

        int sum1 = 0;
        startTime = System.currentTimeMillis();
        for (ExampleStore exampleStore : fastList) {
            sum1 += Integer.parseInt(exampleStore.superSuperIdentifier);
        }
        long durationFastList = System.currentTimeMillis() - startTime;

        memInitial = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        MongirlList<ExampleStore> memoryLessList = new MongirlList<>(MongirlTests.testMongirl, ExampleStore.class, true, true);
        long memMemLessList = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - memInitial;

        int sum2 = 0;
        startTime = System.currentTimeMillis();
        for (ExampleStore exampleStore : memoryLessList) {
            sum2 += Integer.parseInt(exampleStore.superSuperIdentifier);
        }
        long durationMemLessList = System.currentTimeMillis() - startTime;

        assertEquals(sum1, sum2);

        String mflStr = String.valueOf(memFastList / 1000000.);
        String mmllStr = String.valueOf(memMemLessList / 1000000.);

        System.out.println("+ List type  + RAM [MB]      + Time [ms]     +");
        System.out.println("+ Fast List  + " + mflStr + fillWithSpaces(13 - mflStr.length()) + " + "
                + durationFastList + fillWithSpaces(13 - String.valueOf(durationFastList).length()) + " +");
        System.out.println("+ Space List + " + mmllStr + fillWithSpaces(13 - mmllStr.length()) + " + "
                + durationMemLessList + fillWithSpaces(13 - String.valueOf(durationMemLessList).length()) + " +");

        assertTrue(durationFastList < durationMemLessList);
        assertTrue(memMemLessList < memFastList);
    }

    private String fillWithSpaces(int n) {
        StringBuilder builder = new StringBuilder();
        builder.append(" ".repeat(Math.max(0, n)));
        return builder.toString();
    }

    @BeforeEach
    public void cleanDB() {
        clearUpDB();
    }

    @AfterAll
    public static void clearUpDB() {
        MongirlTests.cleanUp();
    }
}
