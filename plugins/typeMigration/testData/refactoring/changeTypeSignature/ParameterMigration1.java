import java.util.*;

class Test {
   class C<T> {
        List<T> l;

        void bar(Map<T, T> t){}

        void f(T t){}

    }

    class D extends C<Strin<caret>g> {
        void foo(String s) {
            f(s);
        }

        public void main() {
            for (String integer : l) {

            }
        }

        void bar(Map<String, String> t) {
            super.bar(t);
        }
    }

}