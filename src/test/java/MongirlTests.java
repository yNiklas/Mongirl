import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import de.yniklas.mongirl.Mongirl;
import de.yniklas.mongirl.Pair;
import de.yniklas.mongirl.examples.ExampleArrayClass;
import de.yniklas.mongirl.examples.ExampleDataclass;
import de.yniklas.mongirl.examples.ExampleDoubleConnection1;
import de.yniklas.mongirl.examples.ExampleEnum;
import de.yniklas.mongirl.examples.ExampleFolded;
import de.yniklas.mongirl.examples.ExampleSubClass;
import de.yniklas.mongirl.examples.ExampleSubObject;
import de.yniklas.mongirl.examples.ExampleSuperclass;
import org.bson.BsonObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MongirlTests {
    private static Mongirl testMongirl;
    private static MongoDatabase DB;

    @BeforeAll
    public static void initDB() {
        testMongirl = new Mongirl("localhost", 27017, "test");

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(
                        new ServerAddress("localhost", 27017)
                ))).build();

        MongoClient client = MongoClients.create(settings);
        DB = client.getDatabase("test");
        DB.drop();
    }

    @Test
    public void testInsertOne() {
        testMongirl.store(new ExampleFolded("testInsertOne"));
        List<ExampleFolded> exampleFoldeds = testMongirl.decodeAll(ExampleFolded.class);

        ExampleFolded eq = null;
        for (ExampleFolded exampleFolded : exampleFoldeds) {
            if (exampleFolded.sub.haha.equals("testInsertOne")) {
                eq = exampleFolded;
            }
        }

        assertNotNull(eq);
    }

    @Test
    public void testDecode() {
        assertEquals(testMongirl.decodeAll(ExampleFolded.class).get(0).idd, "28");
    }

    @Test
    public void testFolded() {
        testMongirl.store(new ExampleFolded("testFolded"));
        List<ExampleFolded> exampleFoldeds = testMongirl.decodeAll(ExampleFolded.class);

        ExampleFolded eq = null;
        for (ExampleFolded exampleFolded : exampleFoldeds) {
            if (exampleFolded.subs.size() >= 2 && exampleFolded.subs.get(1).haha.equals("testFolded")) {
                eq = exampleFolded;
            }
        }

        assertNotNull(eq);
    }

    @Test
    public void testDataclass() {
        testMongirl.store(new ExampleDataclass("auch dabei", 5));
        assertEquals(testMongirl.decodeAll(ExampleDataclass.class).get(0).id, 5);
    }

    @Test
    public void testDecodeFromFilters() {
        int random = (int) (Math.random() * 1000);
        testMongirl.store(new ExampleFolded(String.valueOf(random)));
        ExampleFolded decoded = testMongirl.decodeFromFilters(ExampleFolded.class, new Pair("idd", "28"), new Pair("sub", new ExampleSubObject(String.valueOf(random))));
        assertEquals(decoded.idd, "28");
        assertEquals(decoded.sub.haha, String.valueOf(random));
    }

    @Test
    public void testClasspath() {
        ExampleDoubleConnection1 decoded = testMongirl.decodeTo(ExampleDoubleConnection1.class, ((BsonObjectId) testMongirl.store(new ExampleDoubleConnection1())).getValue());
        assertEquals(decoded.iddd, decoded.connection2s.get(0).connection1.iddd);
    }

    @Test
    public void testEnum() {
        testMongirl.store(new ExampleEnum(6));
        assertEquals("TYPO2", testMongirl.decodeFromFilters(ExampleEnum.class, new Pair("id", 6)).tip.toString());
    }

    @Test
    public void testInheritance() {
        ExampleSuperclass x = new ExampleSubClass();
        testMongirl.store(x);

        assertEquals("20", testMongirl.decodeAll(ExampleSubClass.class).get(0).getSuperInt());
    }

    @Test
    public void testArray() {
        testMongirl.store(new ExampleArrayClass(5));
        List<ExampleArrayClass> exampleArrayClasses = testMongirl.decodeAll(ExampleArrayClass.class);

        assertEquals(1, exampleArrayClasses.size());
        assertEquals(2, exampleArrayClasses.get(0).nmbrs[1]);
        assertEquals(5, exampleArrayClasses.get(0).enhancedArray.length);
        assertEquals("testuser0", exampleArrayClasses.get(0).enhancedArray[0].haha);
    }
}
