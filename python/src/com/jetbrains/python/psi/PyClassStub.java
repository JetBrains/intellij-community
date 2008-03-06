/*
 * @author max
 */
package com.jetbrains.python.psi;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.SerializerClass;

@SerializerClass(PyClassSerializer.class)
public interface PyClassStub extends NamedStub {
}