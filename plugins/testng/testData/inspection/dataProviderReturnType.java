import org.testng.annotations.*;
import org.testng.annotations.DataProvider;

import java.lang.Object;
import java.lang.String;
import java.util.Iterator;

class MyTest {
  @DataProvider
  public Object[][] someTestData() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][] or Iterator<Object[]>">Object[]</error> someTestData5() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][] or Iterator<Object[]>">String[][]</error> someTestData6() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][] or Iterator<Object[]>">Object</error> someTestData7() {return null;}

  @DataProvider
  public Iterator<Object[]> someTestData2() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][] or Iterator<Object[]>">Iterator<Object></error> someTestData3() {return null;}

  @DataProvider
  public <error descr="Data provider must return Object[][] or Iterator<Object[]>">Iterator<String[]></error> someTestData4() {return null;}
}


