/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTargetExpressionStub extends NamedStub<PyTargetExpression>, PyTypeCommentOwnerStub, PyAnnotationOwnerStub {
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
