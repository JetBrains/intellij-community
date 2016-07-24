public class JBossLogTest {

    private final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(JBossLogTest.class);

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new JBossLogTest().logSomething();
    }
}
