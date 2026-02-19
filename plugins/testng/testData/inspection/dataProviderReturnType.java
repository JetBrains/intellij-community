import org.testng.annotations.*;
import org.testng.annotations.DataProvider;

import java.lang.Object;
import java.lang.String;
import java.util.Iterator;
import java.util.Arrays;

class MyTest {
  @DataProvider
  public Object[][] someTestData() { return null; }

  @DataProvider
  public Object[] someTestData5() { return null; }

  @DataProvider
  public String[][] someTestData6() { return null; }

  @DataProvider
  public Iterator<Object[]> someTestData2() { return null; }

  @DataProvider
  public Iterator<Object> someTestData3() { return null; }

  @DataProvider
  public Iterator<String[]> someTestData4() { return null; }

  class Sample { }

  @DataProvider
  public Object[][][][] createData1() {
    return new Sample[][][][] {
      { new Sample[0][], new Sample[0][] },
      { new Sample[0][], new Sample[][]{} },
    };
  }

  @DataProvider
  public Sample[][][][] createData2() {
    return new Sample[][][][] {
      { new Sample[0][], new Sample[0][] },
      { new Sample[0][], new Sample[][]{} },
    };
  }

  @DataProvider
  public <error descr="Data provider must return either an array or iterator">Object</error> someTestData7() {return null;}
}


