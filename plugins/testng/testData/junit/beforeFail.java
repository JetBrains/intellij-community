public class <caret>Testt extends junit.framework.TestCase {
  public void test() {
    assertEquals("1", "2", "description");
    assertNotNull("");
    assertFalse(false);
    assertTrue("true", true);
    assertNotSame("not same", "1", "2");
    assertNull(null);
    assertNull("description", null);
    assertSame("description", "1", "2");
    fail("fail");
  }
}