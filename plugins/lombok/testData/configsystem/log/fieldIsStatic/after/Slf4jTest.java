public class Slf4jTest {

    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Slf4jTest.class);

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new Slf4jTest().logSomething();
    }
}
