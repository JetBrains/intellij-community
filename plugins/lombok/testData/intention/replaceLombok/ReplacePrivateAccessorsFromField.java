public class ReplacePrivateAccessorsFromField {
  private int fi<caret>eld;

  private int getField() {
    return field;
  }

  private void setField(int field) {
    this.field = field;
  }
}
