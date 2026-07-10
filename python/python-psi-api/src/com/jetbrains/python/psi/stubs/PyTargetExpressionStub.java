// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


public interface PyTargetExpressionStub
  extends NamedStub<PyTargetExpression>, PyTypeCommentOwnerStub, PyAnnotationOwnerStub, PyVersionSpecificStub {
  enum InitializerType {
    ReferenceExpression(1),
    CallExpression(2),
    Custom(3),
    Other(0);

    private final int myIndex;

    InitializerType(int index) {
      myIndex = index;
    }

    public int getIndex() {
      return myIndex;
    }

    public static InitializerType fromIndex(int index) {
      return switch (index) {
        case 1 -> ReferenceExpression;
        case 2 -> CallExpression;
        case 3 -> Custom;
        default -> Other;
      };
    }
  }

  InitializerType getInitializerType();

  @Nullable
  QualifiedName getInitializer();

  @ApiStatus.Internal
  @Nullable
  PyLiteralKind getAssignedLiteralKind();

  /**
   * The textual value of the assigned literal (e.g. {@code "42"}, the decoded string, or {@code "true"}/{@code "false"}),
   * interpreted according to {@link #getAssignedLiteralKind()}. {@code null} when no literal value is stored
   * (e.g. for {@code FLOAT}/{@code NONE}, non-plain strings, or non-literal initializers).
   */
  @ApiStatus.Internal
  @Nullable
  String getAssignedLiteralValue();

  boolean isQualified();

  @ApiStatus.Internal
  @Nullable
  <T> T getCustomStub(Class<T> stubClass);

  @Nullable
  String getDocString();

  boolean hasAssignedValue();
}
