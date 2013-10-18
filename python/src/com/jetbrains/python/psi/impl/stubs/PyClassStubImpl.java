package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyClass;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyClassStub;

import java.util.List;

/**
 * @author max
 */
public class PyClassStubImpl extends StubBase<PyClass> implements PyClassStub {
  private final String myName;
  private final QualifiedName[] mySuperClasses;
  private final List<String> mySlots;
  private final String myDocString;

  public PyClassStubImpl(final String name, StubElement parentStub, final QualifiedName[] superClasses, final List<String> slots,
                         String docString, IStubElementType stubElementType) {
    super(parentStub, stubElementType);
    myName = name;
    mySuperClasses = superClasses;
    mySlots = slots;
    myDocString = docString;
  }

  public String getName() {
    return myName;
  }

  public QualifiedName[] getSuperClasses() {
    return mySuperClasses;
  }

  @Override
  public List<String> getSlots() {
    return mySlots;
  }

  @Override
  public String getDocString() {
    return myDocString;
  }

  @Override
  public String toString() {
    return "PyClassStub(" + myName + ")";
  }
}