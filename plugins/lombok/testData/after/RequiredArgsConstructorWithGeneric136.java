public class RequiredArgsConstructorWithGeneric136<T> {

    private static class Foo<T> {
        private final T object;
        private final int i;

        private Foo(T object, int i) {
            this.object = object;
            this.i = i;
        }

        static <T> Foo<T> of(T object, int i) {
            return new Foo<T>(object, i);
        }

        public static <T> Foo<T> of2(T object, int i) {
            return new Foo<T>(object, i);
        }

        public T getObject() {
            return this.object;
        }

        public int getI() {
            return this.i;
        }
    }

    private <D> Foo<D> createFoo(D t, int i) {
        return new Foo<>(t, i);
    }

    public static void main(String[] args) {
        Foo<String> stringFoo = new Foo<>("", 2);

        Foo<String> foo1 = Foo.of("String2", 123);
        Foo<String> foo2 = Foo.of2("String2", 4423);

        System.out.println(stringFoo);
        System.out.println(foo1);
        System.out.println(foo2);
    }
}