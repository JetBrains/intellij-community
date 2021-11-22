public class ReplacePackageProtectedAccessorsFromField {
  private int fi<caret>eld;

  int getField() {
    return field;
  }

  void setField(int field) {
    this.field = field;
  }
}
