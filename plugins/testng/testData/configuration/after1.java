import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Configuration;
public class Testt {
    <caret>@AfterTest()
    @BeforeSuite()
    public void afterBefore(){
    }

}
