class Test {
    ThreadLocal<String> myS = "";

    void foo() {
        if (myS == null) {
            System.out.println(myS.get());
        }
    }
}