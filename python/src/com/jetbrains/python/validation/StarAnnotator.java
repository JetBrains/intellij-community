/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author yole
 */
public class StarAnnotator extends PyAnnotator {
  @Override
  public void visitPyStarExpression(PyStarExpression node) {
    super.visitPyStarExpression(node);
    if (!(node.getExpression() instanceof PyTargetExpression)) {
      getHolder().createErrorAnnotation(node, "can use starred expression only as assignment target");
    }
  }
}
