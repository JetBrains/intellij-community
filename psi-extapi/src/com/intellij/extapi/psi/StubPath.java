/*
 * @author max
 */
package com.intellij.extapi.psi;

public class StubPath {
  private final StubPath myParentPath;
  private final StubPathElement myElement;

  public StubPath(final StubPath parentPath, final StubPathElement element) {
    myParentPath = parentPath;
    myElement = element;
  }

  public StubPath getParentPath() {
    return myParentPath;
  }

  public StubPathElement getLastElement() {
    return myElement;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof StubPath)) return false;

    final StubPath stubPath = (StubPath)o;

    if (!myElement.equals(stubPath.myElement)) return false;
    if (myParentPath != null ? !myParentPath.equals(stubPath.myParentPath) : stubPath.myParentPath != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myParentPath != null ? myParentPath.hashCode() : 0);
    result = 31 * result + myElement.hashCode();
    return result;
  }

  public String toString() {
    return (myParentPath != null ? myParentPath.toString() : "") + "::" + myElement.toString();
  }
}