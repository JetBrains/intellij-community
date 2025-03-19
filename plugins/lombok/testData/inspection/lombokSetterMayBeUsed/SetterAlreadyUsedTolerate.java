import lombok.Setter;
import lombok.experimental.Tolerate;

@Setter
public class SetterAlreadyUsedTolerate {
  private int number;
  private String codeBlue;

  <warning descr="Field 'codeBlue' may have Lombok @Setter">public void setCodeBlue(String codeBlue) {
    this.codeBlue = codeBlue;
  }</warning>

  @Tolerate
  public void setCodeBlue(boolean codeBlue) {
    this.codeBlue = Boolean.toString(codeBlue);
  }

  public static void main(String[] args) {
    final SetterAlreadyUsedTolerate obj1 = new SetterAlreadyUsedTolerate();
    obj1.setCodeBlue(true);
    obj1.setCodeBlue("false");
    System.out.println(obj1);
  }
}
