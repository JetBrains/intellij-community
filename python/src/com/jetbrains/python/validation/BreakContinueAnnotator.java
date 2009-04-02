/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.validation;

import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.python.PyBundle.message;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 13.06.2005
 * Time: 15:01:05
 */
public class BreakContinueAnnotator extends PyAnnotator {
  @Override public void visitPyBreakStatement(final PyBreakStatement node) {
    if (node.getContainingElement(PyElementTypes.LOOPS) == null) {
      getHolder().createErrorAnnotation(node, message("ANN.break.outside.loop"));
    }
  }

  @Override public void visitPyContinueStatement(final PyContinueStatement node) {
    final PyElement loopStmt = node.getContainingElement(PyElementTypes.LOOPS); // closest loop to contain the 'continue'
    if (loopStmt == null) {
      getHolder().createErrorAnnotation(node, message("ANN.continue.outside.loop"));
      return;
    }
    PyTryExceptStatement tryStmt = node.getContainingElement(PyTryExceptStatement.class);
    if (tryStmt != null) {
      final PyFinallyPart finallyPart = tryStmt.getFinallyPart();
      if (finallyPart != null && PsiTreeUtil.isAncestor(loopStmt, finallyPart, true)) {
          getHolder().createErrorAnnotation(node, message("ANN.cant.continue.in.finally"));
      }
    }
  }
}