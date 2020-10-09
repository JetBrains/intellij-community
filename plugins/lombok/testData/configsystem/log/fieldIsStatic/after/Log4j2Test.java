public class Log4j2Test {

    private final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(Log4j2Test.class);

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new Log4j2Test().logSomething();
    }
}
