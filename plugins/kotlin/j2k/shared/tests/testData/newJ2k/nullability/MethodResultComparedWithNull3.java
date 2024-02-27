//file
interface I {
    String getString();
}

class C {
    void foo(I i) {
        String result = i.getString();
        if (result != null) {
            System.out.println(result);
        }
    }
}