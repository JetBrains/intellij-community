// IGNORE_K2
import java.util.*;

interface I<T extends List<Iterator<String>>> {
}

class C implements I<ArrayList<Iterator<String>>> {
}
