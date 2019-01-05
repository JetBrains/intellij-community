import lombok.extern.slf4j.XSlf4j;

@XSlf4j
public class XSlf4jTest {

    public void logSomething(){
        log.info("Hello World!");
    }

    public static void main(String[] args) {
        new XSlf4jTest().logSomething();
    }
}
