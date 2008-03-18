import javax.swing.JComponent;

class IdentityComplete {
  private int myField;

  public void <caret>method(boolean methodPar) {
    String methodVar = "var value";
    myField += methodPar ? methodVar.length() : this.hashCode();

    JComponent methodAnonymous = new JComponent() {
      private int myMethodAnonymousInt;
    };
  }

  public void context(boolean contextPar) {
    String contextVar = "var value";
    myField += contextPar ? contextVar.length() : this.hashCode();

    JComponent contextAnonymous = new JComponent() {
      private int myContextAnonymousInt;
    };
  }
}
