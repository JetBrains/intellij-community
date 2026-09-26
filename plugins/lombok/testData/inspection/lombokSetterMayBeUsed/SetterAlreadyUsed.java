import lombok.Setter;

@Setter
public class SetterAlreadyUsed {
  private int number;
  private String codeBlue;

  //NO warning descr="Field 'codeBlue' may have Lombok @Setter" !
  public void setCodeBlue(String codeBlue) {
    this.codeBlue = codeBlue;
  }

  public void setCodeBlue(boolean codeBlue) {
    this.codeBlue = Boolean.toString(codeBlue);
  }

  public static void main(String[] args) {
    final SetterAlreadyUsed obj1 = new SetterAlreadyUsed();
    obj1.setCodeBlue(true);
    obj1.setCodeBlue("false");
    System.out.println(obj1);
  }
}
