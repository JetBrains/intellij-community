interface I {
    String string();
}

class C {
    void foo(I i) {
        if (i.string() == null) {
            System.out.println("null");
        }
    }
}