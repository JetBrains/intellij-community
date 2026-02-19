interface I {
    String string();
}

class C {
    void foo(I i, boolean b) {
        String result = i.string();
        if (b) result = null;
        if (result != null) {
            System.out.println(result);
        }
    }
}