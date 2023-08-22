import lombok.extern.log4j.Log4j2;

@Log4j2
public class Log4j2Test {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new Log4j2Test().logSomething();
    }
}
