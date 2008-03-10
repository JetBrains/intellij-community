/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.SerializerClass;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.stubs.PyClassSerializer;

@SerializerClass(PyClassSerializer.class)
public interface PyClassStub extends NamedStub<PyClass> {
}