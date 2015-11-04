import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class SampleT<caret>est {

  @Test
  public void differentAssertions() {
    assertEquals("message", new Integer(1), true ? new Integer(1) : null);
    assertEquals(new Integer(1), true ? new Integer(1) : null);
    assertNotEquals("message", new Integer(1), new Integer(2));
    assertNotEquals(new Integer(1), new Integer(2));
    assertArrayEquals("message", new long[0], true ? new long[0] : null);
    assertArrayEquals(new long[0], true ? new long[0] : null);
    assertEquals(1L, true ? 1L : 0);
    assertEquals("message", 1L, true ? 1L : 0);
    assertSame("message", (Object) Integer.valueOf(1), true ? Integer.valueOf(1) : null);
    assertSame((Object) Integer.valueOf(1), true ? Integer.valueOf(1) : null);
    assertNotSame("message", new Object(), true ? new Object() : null);
    assertNotSame(new Object(), true ? new Object() : null);
    assertEquals("message", new Object[0], true ? new Object[0] : null);
    assertEquals(new Object[0], true ? new Object[0] : null);
  }
}