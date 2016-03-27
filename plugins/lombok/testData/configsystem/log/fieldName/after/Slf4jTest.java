public class Slf4jTest {

    private static final org.slf4j.Logger LOGGER1 = org.slf4j.LoggerFactory.getLogger(Slf4jTest.class);

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new Slf4jTest().logSomething();
    }
}
