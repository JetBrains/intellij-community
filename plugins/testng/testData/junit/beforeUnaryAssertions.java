import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Sample<caret>Test {

  @Test
  public void differentAssertions() {
    assertTrue("message", true);
    assertTrue(true);
    assertFalse("message", false);
    assertFalse(false);
    assertNotNull("message", new Object());
    assertNotNull(new Object());
    assertNull("message", null);
    assertNull(null);
  }
}