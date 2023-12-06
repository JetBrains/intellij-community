public class Foo {
    static class Bar {
        public <T> Comparable<T> bar() {
            return null;
        }
    }

    private int compare(Bar o1, Bar o2) {
        if (null == o1 || null == o1.bar()) return -1;
        if (o2 == null || o2.bar() == null) return 1;
        return o1.bar().compareTo(o2.bar());
    }
}