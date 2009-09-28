import org.testng.annotations.Configuration;
public class Testt {
    <caret>@Configuration(beforeSuite = true, afterTest = true)
    public void afterBefore(){
    }

}
