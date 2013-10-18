package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;

/**
 * @author yole
 */
public class PyAnnotationStubImpl extends StubBase<PyAnnotation> implements PyAnnotationStub {
  protected PyAnnotationStubImpl(final StubElement parent, final IStubElementType elementType) {
    super(parent, elementType);
  }
}
