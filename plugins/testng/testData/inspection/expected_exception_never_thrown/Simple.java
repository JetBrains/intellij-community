import org.testng.annotations.Test;
import java.io.IOException;

public class Simple {
  @Test(expectedExceptions = <warning descr="Expected 'IOException' never thrown in body of 'exceptionTestOne()'">IOException</warning>.class)
  public void exceptionTestOne() {
  }
}