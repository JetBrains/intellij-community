import java.util.List;

class A<T> {
    void foo(List<T> x, Boolean y, T z) {
    }
}

class B extends A<Integer> {
    @Override
    void foo(List<Integer> x, Boolean y, Integer z) {
        if (z == null) return;
    }
}