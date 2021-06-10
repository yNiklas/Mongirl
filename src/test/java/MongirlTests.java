import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import de.yniklas.mongirl.Mongirl;
import de.yniklas.mongirl.Pair;
import de.yniklas.mongirl.examples.ExampleDataclass;
import de.yniklas.mongirl.examples.ExampleFolded;
import de.yniklas.mongirl.examples.ExampleSubObject;
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
        System.out.println(testMongirl.store(new ExampleFolded("12. test")));
    }

    @Test
    public void testDecode() {
        System.out.println(testMongirl.decodeAll(ExampleFolded.class));
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
        System.out.println(testMongirl.decodeFromFilters(ExampleFolded.class, new Pair("idd", "27"), new Pair("sub", new ExampleSubObject("311"))));
    }
}
