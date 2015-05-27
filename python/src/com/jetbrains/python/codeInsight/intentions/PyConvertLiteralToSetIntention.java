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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyConvertLiteralToSetIntention extends PyBaseConvertCollectionLiteralIntention {
  public PyConvertLiteralToSetIntention() {
    super(PySetLiteralExpression.class, "set", "{", "}");
  }

  @Override
  protected boolean isAvailableForCollection(PySequenceExpression literal) {
    return LanguageLevel.forElement(literal).isAtLeast(LanguageLevel.PYTHON27) && !isInTargetPosition(literal);
  }

  private static boolean isInTargetPosition(@NotNull final PySequenceExpression sequenceLiteral) {
    return ContainerUtil.exists(sequenceLiteral.getElements(), new Condition<PyExpression>() {
      @Override
      public boolean value(PyExpression expression) {
        return expression instanceof PyTargetExpression;
      }
    });
  }
}
