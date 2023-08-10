// "Use lombok @Setter for 'bar'" "true"

public class QualifiedClass {
  private int bar;
  private int fieldWithoutSetter;

  public void setBar(int param) {
    //Keep this comment
    QualifiedClass.this.bar<caret> = param;
  }
}