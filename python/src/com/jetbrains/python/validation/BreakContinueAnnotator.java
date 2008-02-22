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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.PyElementTypes;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 13.06.2005
 * Time: 15:01:05
 * To change this template use File | Settings | File Templates.
 */
public class BreakContinueAnnotator extends PyAnnotator {
    @Override public void visitPyBreakStatement(final PyBreakStatement node) {
        if (node.getContainingElement(PyElementTypes.LOOPS) == null) {
            getHolder().createErrorAnnotation(node, "'break' outside of loop");
        }
    }

    @Override public void visitPyContinueStatement(final PyContinueStatement node) {
        if (node.getContainingElement(PyElementTypes.LOOPS) == null) {
            getHolder().createErrorAnnotation(node, "'continue' outside of loop");
            return;
        }
        PyTryExceptStatement tryStatement = node.getContainingElement(PyTryExceptStatement.class);
        if (tryStatement != null) {
            PsiElement parent = node.getParent();
            while (parent != null) {
                if (parent == tryStatement.getFinallyStatementList()) {
                    getHolder().createErrorAnnotation(node, "'continue' not supported inside 'finally' clause");
                    break;
                }
                parent = parent.getParent();
            }
        }
    }
}