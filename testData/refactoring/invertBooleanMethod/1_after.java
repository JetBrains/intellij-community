interface I {
    boolean isFooInverted();
}

class RRR implements I {
    public boolean isFooInverted() {
        return false;
    }

    {
        boolean foo = !isFooInverted();
    }

    void g(I i) {
        boolean foo = !i.isFooInverted();
    }
}
