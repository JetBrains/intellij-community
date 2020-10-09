package de.plushnikov.refactor;

public class ExtraBean extends SuperBean {
  private String stringAnother;
  private float floatPrimitive;

  @Override
  public String getString() {
    System.out.println(super.getString());
    return super.getString();
  }

  @Override
  public void setString(String string) {
    System.out.println(string);
    super.setString(string);
  }

  public String getStringAnother() {
    return stringAnother;
  }

  public void setStringAnother(String stringAnother) {
    this.stringAnother = stringAnother;
  }

  public float getFloatPrimitive() {
    return floatPrimitive;
  }

  public void setFloatPrimitive(float floatPrimitive) {
    this.floatPrimitive = floatPrimitive;
  }
}
