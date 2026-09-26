package a;

public class WallTimeSuiteTest {
  @org.testng.annotations.BeforeClass
  public static void setUpClass() throws InterruptedException {
    Thread.sleep(200);
  }

  @org.testng.annotations.Test
  public void test() throws InterruptedException {
    Thread.sleep(100);
  }
}
