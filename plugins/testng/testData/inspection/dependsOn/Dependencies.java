import org.testng.annotations.*;
 class MyTest {
  @Test(dependsOnMethods = <warning descr="Method 'beforeMethod' is not a test or configuration method.">"beforeMethod"</warning>)
  public void testFoo() throws Exception {
  }

  @Test(dependsOnMethods = "testFoo")
  public void testBar() {}

  @AfterSuite
  protected final void afterSuiteMethod() throws Throwable {
  }

  @BeforeMethod(dependsOnMethods = <warning descr="Method 'afterSuiteMethod' is not annotated with @org.testng.annotations.BeforeMethod">"afterSuiteMethod"</warning>)
  public final void beforeMethod() throws Throwable {
  }

   @Test(dependsOnMethods = <warning descr="Method 'foo*' unknown.">"foo*"</warning>)
   public void testBar2() {}

   @Test(dependsOnMethods = "testBa*")
   public void testBar1() {}
 }


