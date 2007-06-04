import org.testng.annotations.AfterTest;

public class Testt {
    /**
      * @testng.before-suite
     */
    <caret>@AfterTest
    public void afterBefore(){
    }
}
