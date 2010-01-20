package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.stubs.PySingleStarParameterStub;

/**
 * @author yole
 */
public class PySingleStarParameterStubImpl extends StubBase<PySingleStarParameter> implements PySingleStarParameterStub {
  protected PySingleStarParameterStubImpl(final StubElement parent) {
    super(parent, PyElementTypes.SINGLE_STAR_PARAMETER);
  }
}
