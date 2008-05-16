/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.psi.tree.IElementType;

public class StubPath {
  private final StubPath myParentPath;
  private String myId;
  private IElementType myType;


  public StubPath(final StubPath parentPath, final String id, final IElementType type) {
    myParentPath = parentPath;
    myId = id;
    myType = type;
  }

  public StubPath getParentPath() {
    return myParentPath;
  }


  public String getId() {
    return myId;
  }

  public IElementType getType() {
    return myType;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof StubPath)) return false;

    final StubPath stubPath = (StubPath)o;

    if (!myId.equals(stubPath.myId)) return false;
    if (myParentPath != null ? !myParentPath.equals(stubPath.myParentPath) : stubPath.myParentPath != null) return false;
    if (!myType.equals(stubPath.myType)) return false;

    return true;
  }

  public int hashCode() {
    int result = (myParentPath != null ? myParentPath.hashCode() : 0);
    result = 31 * result + myId.hashCode();
    result = 31 * result + myType.hashCode();
    return result;
  }

  public String toString() {
    return new StringBuilder().
        append(myParentPath != null ? myParentPath.toString() : "").
        append("::(").
        append(myType.toString()).
        append(":").
        append(myId).
        append(")").toString();
  }
}