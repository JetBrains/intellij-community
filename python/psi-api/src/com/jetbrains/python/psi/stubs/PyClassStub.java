/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyQualifiedName;

import java.util.List;

public interface PyClassStub extends NamedStub<PyClass> {
  PyQualifiedName[] getSuperClasses();
  List<String> getSlots();
  String getDocString();
}