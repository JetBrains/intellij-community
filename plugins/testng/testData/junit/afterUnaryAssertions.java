import org.testng.Assert;
import org.testng.annotations.Test;

public class SampleTest {

  @Test
  public void differentAssertions() {
    Assert.assertTrue(true, "message");
    Assert.assertTrue(true);
    Assert.assertFalse(false, "message");
    Assert.assertFalse(false);
    Assert.assertNotNull(new Object(), "message");
    Assert.assertNotNull(new Object());
    Assert.assertNull(null, "message");
    Assert.assertNull(null);
  }
}