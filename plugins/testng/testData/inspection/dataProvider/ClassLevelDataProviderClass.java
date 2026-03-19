@org.testng.annotations.Test(dataProviderClass = Provider.class)
public class ClassLevelDataProviderClass {
  @org.testng.annotations.Test(dataProvider = "data")
  public void test() {
  }
}

class Provider {
  @org.testng.annotations.DataProvider
  public static Object[][] data() {
    return null;
  }
}
