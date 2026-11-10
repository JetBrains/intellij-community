import java.util.*;

class Base<T> {
    void foo(List<T> items) {
    }
}

class Sub extends Base<Map<String, Integer>> {
    @Override
    void foo(List<Map<String, Integer>> items) {
    }
}

class KotlinSub extends KotlinBase<Map<String, Integer>> {
    @Override
    public void bar(List<? extends Map<String, Integer>> items) {
    }
}
