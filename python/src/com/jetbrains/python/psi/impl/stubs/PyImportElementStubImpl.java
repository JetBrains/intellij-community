package com.jetbrains.python.psi.impl.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.stubs.PyImportElementStub;

/**
 * @author yole
 */
public class PyImportElementStubImpl extends StubBase<PyImportElement> implements PyImportElementStub {
  private final String myImportedName;
  private final String myAsName;

  public PyImportElementStubImpl(String importedName, String asName, final StubElement parent) {
    super(parent, PyElementTypes.IMPORT_ELEMENT);
    myImportedName = importedName;
    myAsName = asName;
  }

  public String getImportedName() {
    return myImportedName;
  }

  public String getAsName() {
    if (!StringUtil.isEmpty(myAsName)) {
      return myAsName;
    }
    return myImportedName;
  }
}
