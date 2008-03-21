/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyClass;

public interface PyClassStub extends NamedStub<PyClass> {
  String[] getSuperClasses();
}