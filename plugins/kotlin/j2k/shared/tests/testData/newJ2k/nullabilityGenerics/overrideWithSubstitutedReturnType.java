import java.util.List;
import java.util.Map;

class A<T> {
    Map<T, List<T>> foo() {
        throw new UnsupportedOperationException();
    }

    T bar() {
        return null;
    }
}

class B extends A<Integer> {
    @Override
    Map<Integer, List<Integer>> foo() {
        return null;
    }

    @Override
    Integer bar() {
        return 42;
    }
}