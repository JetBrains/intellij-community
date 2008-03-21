/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.stubs.PyClassStub;

public class PyClassStubImpl extends StubBase<PyClass> implements PyClassStub {
  private final String myName;
  private final String[] mySuperClasses;

  public PyClassStubImpl(final String name, StubElement parentStub, final String[] superClasses) {
    super(parentStub, PyElementTypes.CLASS_DECLARATION);
    myName = name;
    mySuperClasses = superClasses;
  }

  public String getName() {
    return myName;
  }

  public String[] getSuperClasses() {
    return mySuperClasses;
  }
}