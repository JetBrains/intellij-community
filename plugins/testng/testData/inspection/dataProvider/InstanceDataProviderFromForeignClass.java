public class InstanceDataProviderFromForeignClass {
  @org.testng.annotations.Test(dataProvider = <warning descr="Data provider from foreign class need to be static">"data"</warning>, dataProviderClass = A.class)
  public void test() {
  }
}

class A {
  @org.testng.annotations.DataProvider
  public Object[][] data () {return null;}
}