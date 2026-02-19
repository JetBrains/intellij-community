public class Log4j2Test {

    private static final org.apache.logging.log4j.Logger LOGGER1 = org.apache.logging.log4j.LogManager.getLogger(Log4j2Test.class);

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new Log4j2Test().logSomething();
    }
}
