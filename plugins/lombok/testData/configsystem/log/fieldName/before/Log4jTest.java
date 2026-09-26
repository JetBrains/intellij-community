import lombok.extern.log4j.Log4j;

@Log4j
public class Log4jTest {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new Log4jTest().logSomething();
    }
}
