import lombok.extern.java.Log;

@Log
public class LogTest {

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new LogTest().logSomething();
    }
}
