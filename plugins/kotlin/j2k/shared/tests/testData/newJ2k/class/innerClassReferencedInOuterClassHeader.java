class Outer<T extends Outer.Nested> implements Comparable<Outer.Nested> {
    Outer(Nested nested) {}

    @Override
    public int compareTo(Nested o) {
        return 0;
    }

    static class Nested {
        Nested(int p) {}
    }
}
