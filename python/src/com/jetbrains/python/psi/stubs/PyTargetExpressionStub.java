package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTargetExpressionStub extends NamedStub<PyTargetExpression> {
  enum InitializerType {
    ReferenceExpression(1),
    CallExpression(2),
    Property(3),
    Other(0);

    private int myIndex;

    InitializerType(int index) {
      myIndex = index;
    }

    public int getIndex() {
      return myIndex;
    }

    public static InitializerType fromIndex(int index) {
      switch (index) {
        case 1: return ReferenceExpression;
        case 2: return CallExpression;
        case 3: return Property;
        default: return Other;
      }
    }
  }

  InitializerType getInitializerType();

  @Nullable
  PyQualifiedName getInitializer();

  /**
   * @return a pack of names assigned in a property() call if this target defines a property, or null.
   */
  @Nullable
  PropertyStubStorage getPropertyPack();
}
