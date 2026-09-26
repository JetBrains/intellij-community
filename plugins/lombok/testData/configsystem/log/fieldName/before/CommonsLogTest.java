import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
public class CommonsLogTest {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new CommonsLogTest().logSomething();
    }
}
