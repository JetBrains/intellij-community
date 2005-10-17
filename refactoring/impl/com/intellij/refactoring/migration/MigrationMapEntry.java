
package com.intellij.refactoring.migration;

/**
 *
 */
public class MigrationMapEntry implements Cloneable {
  private String myOldName;
  private String myNewName;
  private int myType;
  private boolean isRecursive;

  public static final int PACKAGE = 0;
  public static final int CLASS = 1;

  public MigrationMapEntry() {

  }

  public MigrationMapEntry(String oldName, String newName, int type, boolean recursive) {
    myOldName = oldName;
    myNewName = newName;
    myType = type;
    isRecursive = recursive;
  }

  public MigrationMapEntry cloneEntry() {
    MigrationMapEntry newEntry = new MigrationMapEntry();
    newEntry.myOldName = myOldName;
    newEntry.myNewName = myNewName;
    newEntry.myType = myType;
    newEntry.isRecursive = isRecursive;
    return newEntry;
  }

  public String getOldName() {
    return myOldName;
  }

  public String getNewName() {
    return myNewName;
  }

  public boolean isRecursive() {
    return isRecursive;
  }

  public int getType() {
    return myType;
  }

  public void setOldName(String name) {
    myOldName = name;
  }

  public void setNewName(String name) {
    myNewName = name;
  }

  public void setType(int type) {
    myType = type;
  }

  public void setRecursive(boolean val) {
    isRecursive = val;
  }
}
