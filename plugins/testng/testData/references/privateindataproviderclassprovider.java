import org.testng.annotations.*;
public class TestTestNGProvider {
  @Test(dataProviderClass = BaseTest.class, dataProvider = "<caret>")
  public void testMe(String param){}
}

class BaseTest {
   @DataProvider
   private static Object[][] data() {
     return null;
   }
}