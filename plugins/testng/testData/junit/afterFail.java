import org.testng.Assert;
import org.testng.annotations.Test;

public class <caret>Testt {
  @Test
  public void test() {
      Assert.assertEquals("description", "2", "1");
      assert "" != null;
      assert !(false);
      assert true : "true";
      Assert.assertNotSame("2", "1", "not same");
      assert null == null;
      assert null == null : "description";
      Assert.assertSame("2", "1", "description");
      assert false : "fail";
  }
}