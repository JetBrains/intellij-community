package de.plushnikov.manual;

public class BasicClass {
  private String privateField;
  protected int protectedField;
  public float publicField;
  boolean packagePrivateField;

  private String getPrivateField() {
    return privateField;
  }

  protected int getProtectedField() {
    return protectedField;
  }

  public float getPublicField() {
    return publicField;
  }

  boolean getPackagePrivateField() {
    return packagePrivateField;
  }

  BasicClass() {
  }

  public BasicClass(float publicField) {
    this.publicField = publicField;
  }

  protected BasicClass(int protectedField) {
    this.protectedField = protectedField;
  }

  private BasicClass(String privateField) {
    this.privateField = privateField;
  }
}
