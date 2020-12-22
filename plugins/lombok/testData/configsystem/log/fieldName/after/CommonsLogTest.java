public class CommonsLogTest {

    private static final org.apache.commons.logging.Log LOGGER1 = org.apache.commons.logging.LogFactory.getLog(CommonsLogTest.class);

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new CommonsLogTest().logSomething();
    }
}
