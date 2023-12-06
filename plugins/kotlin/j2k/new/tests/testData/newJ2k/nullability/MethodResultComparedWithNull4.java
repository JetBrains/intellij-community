//file
interface I {
    String getString();
}

class C {
    void foo(I i, boolean b) {
        String result = i.getString();
        if (b) result = null;
        if (result != null) {
            System.out.println(result);
        }
    }
}