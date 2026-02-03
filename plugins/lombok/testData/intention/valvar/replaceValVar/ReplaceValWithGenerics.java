import lombok.val;

public class ReplaceValWithGenerics {
  public void testMethod() {
    val<caret> list = new ArrayList<String>();
  }
}
