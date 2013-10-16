package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyTargetExpression;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTargetExpressionStub extends NamedStub<PyTargetExpression> {
  enum InitializerType {
    ReferenceExpression(1),
    CallExpression(2),
    Custom(3),
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
        case 3: return Custom;
        default: return Other;
      }
    }
  }

  InitializerType getInitializerType();

  @Nullable
  QualifiedName getInitializer();

  boolean isQualified();

  @Nullable
  <T extends CustomTargetExpressionStub> T getCustomStub(Class<T> stubClass);

  @Nullable
  String getDocString();
}
