//file
interface I {
    String getString();
}

class C {
    void foo(I i) {
        final String result = i.getString();
        if (result != null) {
            System.out.println(result);
        }
    }
}