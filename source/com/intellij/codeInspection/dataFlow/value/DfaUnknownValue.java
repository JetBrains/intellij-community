/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 11:23:22 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

public class DfaUnknownValue extends DfaValue {
  private static volatile DfaUnknownValue myInstance;

  public static DfaUnknownValue getInstance() {
    if (myInstance == null) {
      myInstance = new DfaUnknownValue();
    }
    return myInstance;
  }

  private DfaUnknownValue() {
    super(null);
  }

  public DfaValue createNegated() {
    return this;
  }

  public String toString() {
    return "<unknown>";
  }

  public boolean equals(Object obj) {
    return obj == this;
  }

  public int hashCode() {
    return 0;
  }

  public int getID() {
    return 0;
  }

}
