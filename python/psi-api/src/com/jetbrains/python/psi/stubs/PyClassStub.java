/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyClass;
import com.intellij.psi.util.QualifiedName;

import java.util.List;

public interface PyClassStub extends NamedStub<PyClass> {
  QualifiedName[] getSuperClasses();
  List<String> getSlots();
  String getDocString();
}