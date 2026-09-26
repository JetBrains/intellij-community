public abstract class AbstractClassProvider {
  @org.testng.annotations.Test(dataProvider = "data")
  public void test() {
  }
}

class ConcreteTest extends AbstractClassProvider {
  @org.testng.annotations.DataProvider
  public Object[][] data() {
    return null;
  }
}
