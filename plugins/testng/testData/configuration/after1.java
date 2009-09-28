import org.testng.annotations.Configuration;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.AfterTest;

public class Testt {
    <caret>@AfterTest()
    @BeforeSuite()
    public void afterBefore(){
    }

}
