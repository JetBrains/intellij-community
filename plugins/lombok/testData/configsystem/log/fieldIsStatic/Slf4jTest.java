import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Slf4jTest {

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new Slf4jTest().logSomething();
    }
}
