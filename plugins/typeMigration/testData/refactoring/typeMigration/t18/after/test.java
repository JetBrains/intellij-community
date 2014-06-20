import java.util.*;
class Test {
    B f;
    void bar(Set<A> s) {
       for (String s : f) {}
    }
}

class A<Y> extends List<String> {}

class B extends A<Integer> {}