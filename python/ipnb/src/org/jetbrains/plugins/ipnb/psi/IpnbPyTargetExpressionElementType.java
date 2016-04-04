/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.stubs.PyTargetExpressionElementType;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.NotNull;

public class IpnbPyTargetExpressionElementType extends PyTargetExpressionElementType {
  public IpnbPyTargetExpressionElementType() {
    super("IPNB_TARGET_EXPRESSION");
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new IpnbPyTargetExpression(node);
  }

  public PyTargetExpression createPsi(@NotNull final PyTargetExpressionStub stub) {
    return new IpnbPyTargetExpression(stub);
  }

}
