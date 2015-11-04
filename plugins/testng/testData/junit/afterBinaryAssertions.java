import org.testng.Assert;
import org.testng.annotations.Test;

public class SampleTest {

  @Test
  public void differentAssertions() {
    Assert.assertEquals(true ? new Integer(1) : null, new Integer(1), "message");
    Assert.assertEquals(true ? new Integer(1) : null, new Integer(1));
    Assert.assertNotEquals(new Integer(2), new Integer(1), "message");
    Assert.assertNotEquals(new Integer(2), new Integer(1));
    Assert.assertEquals(true ? new long[0] : null, new long[0], "message");
    Assert.assertEquals(true ? new long[0] : null, new long[0]);
    Assert.assertEquals(true ? 1L : 0, 1L);
    Assert.assertEquals(true ? 1L : 0, 1L, "message");
    Assert.assertSame(true ? Integer.valueOf(1) : null, (Object) Integer.valueOf(1), "message");
    Assert.assertSame(true ? Integer.valueOf(1) : null, (Object) Integer.valueOf(1));
    Assert.assertNotSame(true ? new Object() : null, new Object(), "message");
    Assert.assertNotSame(true ? new Object() : null, new Object());
    Assert.assertEquals(true ? new Object[0] : null, new Object[0], "message");
    Assert.assertEquals(true ? new Object[0] : null, new Object[0]);
  }
}