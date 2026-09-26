@org.testng.annotations.Test(dataProviderClass = InheritedProvider.class)
class InheritedClassLevelBase {
}

public class InheritedClassLevelDataProviderClass extends InheritedClassLevelBase {
  @org.testng.annotations.Test(dataProvider = "data")
  public void test() {
  }
}

class InheritedProvider {
  @org.testng.annotations.DataProvider
  public static Object[][] data() {
    return null;
  }
}
