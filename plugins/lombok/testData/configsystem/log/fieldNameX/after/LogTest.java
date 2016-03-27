public class LogTest {

    private static final java.util.logging.Logger LOGGER1 = java.util.logging.Logger.getLogger(LogTest.class.getName());

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new LogTest().logSomething();
    }
}
