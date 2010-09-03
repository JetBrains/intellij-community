import org.testng.annotations.*;
class TestTestNGProvider {
  @DataProvider
  private static Object[][] data() {
     return null;
   }

  @Test(dataProvider = "<caret>")
  public void testMe(String param){}
}