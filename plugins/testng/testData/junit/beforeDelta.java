import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Sample<caret>Test {

  @Test
  public void differentAssertions() {
    assertArrayEquals("message", new double[0], true ? new double[0] : null, 0d);
    assertArrayEquals(new double[0], true ? new double[0] : null, 0d);
    assertEquals("message", 0d, 1d, 2d);
    assertEquals(1d, 2d, 0d);

  }
}