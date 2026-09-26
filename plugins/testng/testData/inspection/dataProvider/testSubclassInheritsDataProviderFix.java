abstract class AbstractBaseForFix {
  @org.testng.annotations.Test(dataProvider = "data")
  public void testMe(String s) {}
}

class SubclassWithFix extends AbstractBaseForFix {
}