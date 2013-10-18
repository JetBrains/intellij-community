package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

/**
 * @author yole
 */
public class PyExceptPartStubImpl extends StubBase<PyExceptPart> implements PyExceptPartStub {
  protected PyExceptPartStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.EXCEPT_PART);
  }
}
