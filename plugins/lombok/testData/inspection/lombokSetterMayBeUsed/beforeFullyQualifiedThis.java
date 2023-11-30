// "Use lombok @Setter for 'bar'" "true"

package project;

public class OneFullyQualifiedClass {
  private int bar;
  private int fieldWithoutSetter;

  public void setBar(int param) {
    project.OneFullyQualifiedClass.this.bar<caret> = param;
  }
}