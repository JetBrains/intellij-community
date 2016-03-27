import lombok.extern.java.Log;

@Log
public class LogTest {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new LogTest().logSomething();
    }
}
