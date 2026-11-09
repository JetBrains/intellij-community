import java.util.List;
import java.util.Map;

interface A<T, S> {
    List<Map<T, S>> foo();
}

class B<X> implements A<X, Integer> {
    @Override
    public List<Map<X, Integer>> foo() {
        throw new UnsupportedOperationException();
    }
}