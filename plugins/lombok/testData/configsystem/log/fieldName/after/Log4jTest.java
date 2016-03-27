public class Log4jTest {

    private static final org.apache.log4j.Logger LOGGER1 = org.apache.log4j.Logger.getLogger(Log4jTest.class);

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new Log4jTest().logSomething();
    }
}
