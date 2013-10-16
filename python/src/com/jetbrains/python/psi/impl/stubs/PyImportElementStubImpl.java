package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyImportElementStubImpl extends StubBase<PyImportElement> implements PyImportElementStub {
  private final QualifiedName myImportedQName;
  private final String myAsName;

  public PyImportElementStubImpl(@Nullable QualifiedName importedQName, String asName, final StubElement parent,
                                 IStubElementType elementType) {
    super(parent, elementType);
    myImportedQName = importedQName;
    myAsName = asName;
  }

  @Nullable
  public QualifiedName getImportedQName() {
    return myImportedQName;
  }

  public String getAsName() {
    return myAsName;
  }

  @Override
  public String toString() {
    return "PyImportElementStub(importedQName=" + myImportedQName + " asName=" + myAsName + ")";
  }
}
