interface I {
    boolean isFoo();
}

class RRR implements I {
    public boolean <caret>isFoo() {
        return true;
    }

    {
        boolean foo = isFoo();
    }

    void g(I i) {
        boolean foo = i.isFoo();
    }
}
