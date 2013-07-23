
import org.testng.annotations.*;
public class MyTest {
  @Test(<warning descr="Method 'beforeMethod' is not a test or configuration method.">dependsOnMethods = "beforeMethod"</warning>)
  public void testFoo() throws Exception {
  }

  @Test(dependsOnMethods = "testFoo")
  public void testBar() {}

  @AfterSuite
  protected final void afterSuiteMethod() throws Throwable {
  }

  @BeforeMethod(<warning descr="Method 'afterSuiteMethod' is not annotated with @org.testng.annotations.BeforeMethod">dependsOnMethods = "afterSuiteMethod"</warning>)
  public final void beforeMethod() throws Throwable {
  }
}


