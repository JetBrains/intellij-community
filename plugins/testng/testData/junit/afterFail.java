import org.testng.Assert;
import org.testng.annotations.Test;

public class Testt {
  @Test
  public void test() {
    Assert.assertEquals("description", "2", "1");
    Assert.assertNotNull("");
    Assert.assertFalse(false);
    Assert.assertTrue(true, "true");
    Assert.assertNotSame("2", "1", "not same");
    Assert.assertNull(null);
    Assert.assertNull(null, "description");
    Assert.assertSame("2", "1", "description");
    Assert.fail("fail");
  }
}