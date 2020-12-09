import lombok.extern.log4j.Log4j;

@Log4j
public class Log4jTest {

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new Log4jTest().logSomething();
    }
}
