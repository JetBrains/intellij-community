import lombok.extern.slf4j.XSlf4j;

@XSlf4j
public class XSlf4jTest {

    public void logSomething(){
        LOGGER1.info("Hello World!");
    }

    public static void main(String[] args) {
        LOGGER1.info("Test");
        new XSlf4jTest().logSomething();
    }
}
