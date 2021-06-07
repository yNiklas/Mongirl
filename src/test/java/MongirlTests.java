import de.yniklas.mongirl.Mongirl;
import de.yniklas.mongirl.examples.ExampleStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MongirlTests {
    @Test
    public void testInsertOne() {
        System.out.println(new Mongirl("localhost", 27017, "test").store(new ExampleStore("example1")));
    }

    @Test
    public void testDecode() {
        System.out.println(new Mongirl("localhost", 27017, "test").decodeAll(ExampleStore.class));
    }
}
