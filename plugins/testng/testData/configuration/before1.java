import org.testng.annotations.Configuration;
public class Testt {
    <caret>@org.testng.annotations.Configuration(beforeSuite = true, afterTest = true)
    public void afterBefore(){
    }

}
