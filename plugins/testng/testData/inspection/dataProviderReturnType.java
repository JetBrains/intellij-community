import org.testng.annotations.*;
import org.testng.annotations.DataProvider;

import java.lang.Object;
import java.lang.String;
import java.util.Iterator;
import java.util.Arrays;

class MyTest {
  @DataProvider
  public Object[][] someTestData() {return null;}

  @DataProvider
  public Object[] someTestData5() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][]/Object[] or Iterator<Object[]>/Iterator<Object>">String[][]</error> someTestData6() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][]/Object[] or Iterator<Object[]>/Iterator<Object>">Object</error> someTestData7() {return null;}

  @DataProvider
  public Iterator<Object[]> someTestData2() {return null;}

  @DataProvider
  public Iterator<Object> someTestData3() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][]/Object[] or Iterator<Object[]>/Iterator<Object>">Iterator<String[]></error> someTestData4() {return null;}

  @DataProvider(name = "test1")
  public Object[][][][] createData1() {
    return new Sample[][][][] {
      {new Sample[0][], new Sample[0][]},
      { new Sample[0][], new Sample[][]{}},
    };
  }

  @DataProvider(name = "test1")
  public Sample[][][][] createData2() {
    return new Sample[][][][] {
      {new Sample[0][], new Sample[0][]},
      { new Sample[0][], new Sample[][]{}},
    };
  }

  @Test(dataProvider = "test1")
  public void verifyData1(Sample [][] n1, Sample [][] n2) {
    System.out.println(n1 + " " + n2);
  }

  @DataProvider(name = "test2")
  public Iterator<MyCustomData> createData() {
    return Arrays.asList(new MyCustomData()).iterator();
  }

  @Test(dataProvider = "test2")
  public void testMethod(MyCustomData data) {
    System.out.println("Data is: " + data);
  }


  private class MyCustomData {}

  class Sample {}
}


