public abstract class NotReplaceSetterWithWrongParamFromField {
  private int fi<caret>eld;

  public void setField(long field) {
    this.field = (int) field;
  }
}
