import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
public class CommonsLogTest {

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new CommonsLogTest().logSomething();
    }
}
