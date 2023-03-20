class Test {

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(Test.class);

    public void logHallo() {
        log.info("Hello!");
    }

    public static void main(String[] args) {
        Test test = new Test();
        test.logHallo();
    }

}