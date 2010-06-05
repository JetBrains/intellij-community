package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyTargetExpressionStubImpl extends StubBase<PyTargetExpression> implements PyTargetExpressionStub {
  private final String myName;
  private final PyQualifiedName myInitializer;

  private final PropertyStubStorage myPropertyPack;

  public PyTargetExpressionStubImpl(String name, PropertyStubStorage propertyPack, StubElement parent) {
    super(parent, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    myInitializer = null;
    myPropertyPack = propertyPack;
  }
  
  public PyTargetExpressionStubImpl(final String name, final PyQualifiedName initializer, final StubElement parentStub) {
    super(parentStub, PyElementTypes.TARGET_EXPRESSION);
    myName = name;
    myInitializer = initializer;
    myPropertyPack = null;
  }

  public String getName() {
    return myName;
  }

  public PyQualifiedName getInitializer() {
    return myInitializer;
  }

  @Nullable
  public PropertyStubStorage getPropertyPack() {
    return myPropertyPack;
  }
}
