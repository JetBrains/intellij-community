package com.intellij.codeInspection.dataFlow.value;

public class DfaValue {
  private final int myID;

  protected DfaValue() {
    final DfaValueFactory factory = DfaValueFactory.getInstance();
    myID = factory.createID(this);
    factory.registerID(this);
  }

  public int getID() {
    return myID;
  }

  public DfaValue createNegated() {
    return DfaUnknownValue.getInstance();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof DfaValue)) return false;
    return getID() == ((DfaValue) obj).getID();
  }

  public int hashCode() {
    return getID();
  }
}
