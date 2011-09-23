package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyClassStub;

import java.util.List;

/**
 * @author max
 */
public class PyClassStubImpl extends StubBase<PyClass> implements PyClassStub {
  private final String myName;
  private final PyQualifiedName[] mySuperClasses;
  private final List<String> mySlots;

  public PyClassStubImpl(final String name, StubElement parentStub, final PyQualifiedName[] superClasses, final List<String> slots,
                         IStubElementType stubElementType) {
    super(parentStub, stubElementType);
    myName = name;
    mySuperClasses = superClasses;
    mySlots = slots;
  }

  public String getName() {
    return myName;
  }

  public PyQualifiedName[] getSuperClasses() {
    return mySuperClasses;
  }

  @Override
  public List<String> getSlots() {
    return mySlots;
  }
}