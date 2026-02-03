import org.testng.annotations.*;
class TestTestNGProvider extends BaseTest {
  @Test(dataProvider = "<caret>")
  public void testMe(String param){}
}

class BaseTest {
  @DataProvider
  private static Object[][] data() {
     return null;
   }
}