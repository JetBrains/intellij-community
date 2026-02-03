package a;

public class Test1 {
  @org.testng.annotations.Test(testName = "myName")
  public void simple() {
    System.out.println("sample output");
  }

  @org.testng.annotations.Test
  public void simple2() {
    System.out.println("sample output");
  }
}