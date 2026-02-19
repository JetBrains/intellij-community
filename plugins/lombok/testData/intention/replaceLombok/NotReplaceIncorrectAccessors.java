public class ReplaceGetterFromField {
  private int fi<caret>eld;

  public int getField() {
    System.out.println("some stub");
    return 0;
  }

  public void setField(int field) {
    System.out.println("Additional monitoring");
    this.field = field;
  }
}
