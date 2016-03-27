public class CommonsLogTest {

    private final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(CommonsLogTest.class);

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new CommonsLogTest().logSomething();
    }
}
