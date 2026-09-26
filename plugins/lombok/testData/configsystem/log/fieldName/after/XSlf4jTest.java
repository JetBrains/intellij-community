public class XSlf4jTest {

    private static final org.slf4j.ext.XLogger LOGGER1 = org.slf4j.ext.XLoggerFactory.getXLogger(XSlf4jTest.class);

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new XSlf4jTest().logSomething();
    }
}
