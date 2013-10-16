package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTargetExpression;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyTargetExpressionStubImpl extends StubBase<PyTargetExpression> implements PyTargetExpressionStub {
  private final String myName;
  private final InitializerType myInitializerType;
  private final QualifiedName myInitializer;
  private final boolean myQualified;
  @Nullable private final String myDocString;

  private final CustomTargetExpressionStub myCustomStub;

  public PyTargetExpressionStubImpl(String name,
                                    @Nullable String docString,
                                    CustomTargetExpressionStub customStub,
                                    StubElement parent) {
    super(parent, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    myInitializerType = InitializerType.Custom;
    myInitializer = null;
    myQualified = false;
    myCustomStub = customStub;
    myDocString = docString;
  }
  
  public PyTargetExpressionStubImpl(final String name, @Nullable String docString, final InitializerType initializerType,
                                    final QualifiedName initializer,
                                    final boolean qualified,
                                    final StubElement parentStub) {
    super(parentStub, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    assert initializerType != InitializerType.Custom;
    myInitializerType = initializerType;
    myInitializer = initializer;
    myQualified = qualified;
    myCustomStub = null;
    myDocString = docString;
  }

  public String getName() {
    return myName;
  }

  public InitializerType getInitializerType() {
    return myInitializerType;
  }

  public QualifiedName getInitializer() {
    return myInitializer;
  }

  @Override
  public boolean isQualified() {
    return myQualified;
  }

  @Nullable
  @Override
  public <T extends CustomTargetExpressionStub> T getCustomStub(Class<T> stubClass) {
    if (stubClass.isInstance(myCustomStub)) {
      return stubClass.cast(myCustomStub);
    }
    return null;
  }

  @Nullable
  @Override
  public String getDocString() {
    return myDocString;
  }

  @Override
  public String toString() {
    return "PyTargetExpressionStub(name=" + myName + ")";
  }
}
