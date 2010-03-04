package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;

import java.util.List;

/**
 * @author yole
 */
public interface PyImportElementStub extends StubElement<PyImportElement> {
  List<String> getImportedQName();
  String getAsName();
}
