import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Slf4jTest {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new Slf4jTest().logSomething();
    }
}
