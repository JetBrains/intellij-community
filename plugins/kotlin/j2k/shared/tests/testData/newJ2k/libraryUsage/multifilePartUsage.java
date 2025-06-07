import kotlin.collections.CollectionsKt;
import java.util.List;

public class JJ {
    private List<Integer> bug(List<String> list) {
        return CollectionsKt.map(list, string -> string.length());
    }
}
