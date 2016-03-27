public class LogTest {

    private final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LogTest.class.getName());

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new LogTest().logSomething();
    }
}
