public class Log4jTest {

    private final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(Log4jTest.class);

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new Log4jTest().logSomething();
    }
}
