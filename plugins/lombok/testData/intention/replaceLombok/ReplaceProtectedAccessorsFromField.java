public class ReplaceProtectedAccessorsFromField {
  private int fi<caret>eld;

  protected int getField() {
    return field;
  }

  protected void setField(int field) {
    this.field = field;
  }
}
