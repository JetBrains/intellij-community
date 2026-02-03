import org.testng.annotations.*;
public class TestTestNGProvider extends BaseTest {
  @Test(dataProvider = "<caret>")
  public void testMe(String param){}
}

class BaseTest {
   @DataProvider
   static Object[][] data() {
     return null;
   }
}