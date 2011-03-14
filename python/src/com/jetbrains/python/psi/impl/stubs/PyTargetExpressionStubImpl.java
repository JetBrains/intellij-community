package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyTargetExpressionStubImpl extends StubBase<PyTargetExpression> implements PyTargetExpressionStub {
  private final String myName;
  private final InitializerType myInitializerType;
  private final PyQualifiedName myInitializer;

  private final CustomTargetExpressionStub myCustomStub;

  public PyTargetExpressionStubImpl(String name, CustomTargetExpressionStub customStub, StubElement parent) {
    super(parent, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    myInitializerType = InitializerType.Custom;
    myInitializer = null;
    myCustomStub = customStub;
  }
  
  public PyTargetExpressionStubImpl(final String name, final InitializerType initializerType,
                                    final PyQualifiedName initializer, final StubElement parentStub) {
    super(parentStub, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    assert initializerType != InitializerType.Custom;
    myInitializerType = initializerType;
    myInitializer = initializer;
    myCustomStub = null;
  }

  public String getName() {
    return myName;
  }

  public InitializerType getInitializerType() {
    return myInitializerType;
  }

  public PyQualifiedName getInitializer() {
    return myInitializer;
  }

  @Nullable
  @Override
  public <T extends CustomTargetExpressionStub> T getCustomStub(Class<T> stubClass) {
    if (stubClass.isInstance(myCustomStub)) {
      return stubClass.cast(myCustomStub);
    }
    return null;
  }

  @Override
  public String toString() {
    return "PyTargetExpressionStub(name=" + myName + ")";
  }
}
