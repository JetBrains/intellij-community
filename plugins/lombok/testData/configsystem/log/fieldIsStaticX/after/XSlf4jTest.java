public class XSlf4jTest {

    private final org.slf4j.ext.XLogger log = org.slf4j.ext.XLoggerFactory.getXLogger(XSlf4jTest.class);

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new XSlf4jTest().logSomething();
    }
}
