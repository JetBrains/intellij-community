// IGNORE_K2
import java.util.*;

class A{
    Collection<String> createCollection() {
        return new ArrayList<>();
    }

    Collection<String> foo() {
        Collection<String> collection = createCollection();
        collection.add("a");
        return collection;
    }
}