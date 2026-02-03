interface I {
    String string();
}

class C {
    void foo(I i) {
        final String result = i.string();
        if (result != null) {
            System.out.println(result);
        }
    }
}