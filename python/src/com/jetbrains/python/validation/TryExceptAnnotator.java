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
package com.jetbrains.python.validation;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;

/**
 * Marks misplaced default 'except' clauses.
 *
 * @author yole
 */
public class TryExceptAnnotator extends PyAnnotator {
  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    PyExceptPart[] exceptParts = node.getExceptParts();
    boolean haveDefaultExcept = false;
    for (PyExceptPart part : exceptParts) {
      if (haveDefaultExcept) {
        getHolder().createErrorAnnotation(part, PyBundle.message("ANN.default.except.must.be.last"));
      }
      if (part.getExceptClass() == null) {
        haveDefaultExcept = true;
      }
    }
  }
}
