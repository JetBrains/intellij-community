import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class SampleTest {

  @Test
  public void differentAssertions() {
    AssertJUnit.assertArrayEquals("message", new double[0], true ? new double[0] : null, 0d);
    AssertJUnit.assertArrayEquals(new double[0], true ? new double[0] : null, 0d);
    AssertJUnit.assertEquals("message", 0d, 1d, 2d);
    AssertJUnit.assertEquals(1d, 2d, 0d);

  }
}