package de.plushnikov.refactor;

public class SuperBean {
  private String string;
  private Boolean booleanObject;
  private boolean booleanPrimitive;
  private Integer integer;
  private long number;

  public String getString() {
    return string;
  }

  public void setString(String string) {
    this.string = string;
  }

  public Boolean getBooleanObject() {
    return booleanObject;
  }

  public void setBooleanObject(Boolean booleanObject) {
    this.booleanObject = booleanObject;
  }

  public boolean isBooleanPrimitive() {
    return booleanPrimitive;
  }

  public void setBooleanPrimitive(boolean booleanPrimitive) {
    this.booleanPrimitive = booleanPrimitive;
  }

  public Integer getInteger() {
    return integer;
  }

  public void setInteger(Integer integer) {
    this.integer = integer;
  }

  public long getNumber() {
    return number;
  }

  public void setNumber(long number) {
    this.number = number;
  }
}
