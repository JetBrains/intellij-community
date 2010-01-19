import org.testng.annotations.AfterTest;

public class Testt {
    /**
     * @testng.after-test
     * @testng.before-suite
     */
    <caret>public void afterBefore(){
    }
}
