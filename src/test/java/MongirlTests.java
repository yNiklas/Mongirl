import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
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
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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
    }

    @Test
    public void testInsertOne() {
        assertEquals(((ObjectId) testMongirl.store(new ExampleFolded("12. test"))).toString(), "619ffac06d45b90686db4bcc");
    }

    @Test
    public void testDecode() {
        assertEquals(testMongirl.decodeAll(ExampleFolded.class).get(0).idd, "28");
    }

    @Test
    public void testFolded() {
        Bson id = Filters.eq("_id", new ObjectId("60bfa25f2a7366644ff06f89"));
        ExampleFolded folded = testMongirl.decodeTo(ExampleFolded.class, (ObjectId) DB.getCollection("folded").find(id).first().get("_id"));
        assertEquals(folded.subs.get(1).haha, "soos");
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
        testMongirl.store(new ExampleEnum());
        assertEquals(testMongirl.decodeFromFilters(ExampleEnum.class, new Pair("id", 6)).tip.toString(), "TYPO2");
    }

    @Test
    public void testInheritance() {
        ExampleSuperclass x = new ExampleSubClass();
        testMongirl.store(x);

        assertEquals("20", testMongirl.decodeAll(ExampleSubClass.class).get(0).getSuperInt());
    }

    @Test
    public void testArray() {
        testMongirl.store(new ExampleArrayClass());
        testMongirl.decodeAll(ExampleArrayClass.class);
    }
}
