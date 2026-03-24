abstract class AbstractBaseNoProvider {
  @org.testng.annotations.Test(dataProvider = "data")
  public void testMe(String s) {}
}

class <warning descr="Data provider does not exist">SubclassInheritsDataProviderWithoutClass</warning> extends AbstractBaseNoProvider {}

@org.testng.annotations.Test(dataProviderClass = MyDataProvider.class)
class SubclassInheritsDataProviderWithAnnotation extends AbstractBaseNoProvider {}

class SubclassInheritsDataProviderWithAnonymousMethod extends AbstractBaseNoProvider {
  @org.testng.annotations.DataProvider
  public static Object[][] data() {return null;}
}

class SubclassInheritsDataProviderWithNamedMethod extends AbstractBaseNoProvider {
  @org.testng.annotations.DataProvider(name = "data")
  public static Object[][] none() {return null;}
}

class MyDataProvider {
  @org.testng.annotations.DataProvider
  public static Object[][] data() {return null;}
}