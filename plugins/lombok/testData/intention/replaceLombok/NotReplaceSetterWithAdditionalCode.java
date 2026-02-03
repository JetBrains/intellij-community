public abstract class NotReplaceSetterWithAdditionalCode {
  private int field;

  public void set<caret>Field(int field) {
    this.field = field + 1;
  }
}
