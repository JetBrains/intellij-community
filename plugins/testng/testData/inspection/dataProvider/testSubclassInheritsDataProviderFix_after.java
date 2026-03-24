import org.testng.annotations.DataProvider;

abstract class AbstractBaseForFix {
  @org.testng.annotations.Test(dataProvider = "data")
  public void testMe(String s) {}
}

class SubclassWithFix extends AbstractBaseForFix {
    @DataProvider
    public static Object[][] data() {
        return null;
    }
}