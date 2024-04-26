// IGNORE_K2
import java.util.Collection;
import java.util.List;

class C {
    boolean bar(Object o, Collection<String> collection) {
        return o instanceof Collection && collection instanceof List;
    }
}
