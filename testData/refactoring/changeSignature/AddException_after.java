class Test {
    void foo () throws Exception {
    }

    void bar () {
        try {
            foo();
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
