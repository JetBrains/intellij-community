import org.testng.annotations.*;
import org.testng.annotations.DataProvider;

import java.lang.Object;
import java.lang.String;
import java.util.Iterator;

class DuplicatedTest1 {

  @DataProvider
  public Object[][] <error descr="Data provider with name 'someTestData' already exists in context">someTestData</error>() {return null;}

  @DataProvider(name=<error descr="Data provider with name 'someTestData' already exists in context">"someTestData"</error>)
  public Object[][] someTestData2() {return null;}

}

class DuplicatedTest2 {

  @DataProvider(name = <error descr="Data provider with name 'asd' already exists in context">"asd"</error>)
  public Object[][] someTestData() {return null;}

  @DataProvider(name = <error descr="Data provider with name 'asd' already exists in context">"asd"</error>)
  public Object[][] someTestData2() {return null;}

}

class NoDuplicationTest {

  @DataProvider(name = "qwe")
  public Object[][] someTestData() {return null;}

  @DataProvider(name = "asd")
  public Object[][] someTestData2() {return null;}

}

class NoDuplication2Test {

  public Object[][] someTestData() {return null;}

  @DataProvider(name = "someTestData")
  public Object[][] someTestData2() {return null;}

}

abstract class DuplicateInSuperBase {
  @DataProvider(name = "someTestData")
  public static Object[][] someTestData2() {return null;}

  @DataProvider
  public abstract Object[][] getData();
}
class DuplicateInSuper extends DuplicateInSuperBase {
  @DataProvider
  public static Object[][] <error descr="Data provider with name 'someTestData' already exists in context">someTestData</error>() {return null;}

  @DataProvider
  public Object[][] getData() {return null;}
}