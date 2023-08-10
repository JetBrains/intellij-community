class Test {

    private static final org.slf4j.ext.XLogger log = org.slf4j.ext.XLoggerFactory.getXLogger(Test.class);

    public void logHallo() {
        log.info("Hello!");
    }

    public static void main(String[] args) {
        Test test = new Test();
        test.logHallo();
    }

}