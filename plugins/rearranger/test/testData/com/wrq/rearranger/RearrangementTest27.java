public class RearrangerTest27 {
  private boolean constructorMethodType;
  private boolean getterSetterMethodType;
  private int     i;

  public MethodAttributes() {
    init();
  }

  private void init() {
    i = 1;
  }

  public final void setConstructorMethodType(final boolean constructorMethodType) {
    this.constructorMethodType = constructorMethodType;
  }

  public MethodAttributes(int i) {
    init();
    this.i = i;
  }

  private boolean isConstructorMethodType() {
    return constructorMethodType;
  }

  private boolean isGetterSetterMethodType() {
    return getterSetterMethodType;
  }

  private Object getMethodTypePanel() {
    return getMethodTypeInnerPanel();
  }

  public final void setGetterSetterMethodType(final boolean getterSetterMethodType) {
    this.getterSetterMethodType = getterSetterMethodType;
  }

  private JPanel getMethodTypeInnerPanel() {
    setConstructorMethodType(true);
    setGetterSetterMethodType(false);
    return new Object();
  }
}

