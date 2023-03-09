class Test {

    private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(Test.class);

    public void logHallo() {
        log.info("Hello!");
    }

    public static void main(String[] args) {
        Test test = new Test();
        test.logHallo();
    }

}