package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyImportElementStubImpl extends StubBase<PyImportElement> implements PyImportElementStub {
  private final PyQualifiedName myImportedQName;
  private final String myAsName;

  public PyImportElementStubImpl(@Nullable PyQualifiedName importedQName, String asName, final StubElement parent) {
    super(parent, PyElementTypes.IMPORT_ELEMENT);
    myImportedQName = importedQName;
    myAsName = asName;
  }

  @Nullable
  public PyQualifiedName getImportedQName() {
    return myImportedQName;
  }

  public String getAsName() {
    return myAsName;
  }
}
