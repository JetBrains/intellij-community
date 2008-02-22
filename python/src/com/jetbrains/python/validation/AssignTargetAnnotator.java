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

import com.jetbrains.python.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 10.06.2005
 * Time: 23:27:40
 * To change this template use File | Settings | File Templates.
 */
public class AssignTargetAnnotator extends PyAnnotator {
    private enum Operation { Assign, AugAssign, Delete, Except, For }

    @Override public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
        ExprVisitor visitor = new ExprVisitor(Operation.Assign);
        for (PyExpression expr: node.getTargets()) {
            expr.accept(visitor);
        }
    }

    @Override public void visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
        node.getTarget().accept(new ExprVisitor(Operation.AugAssign));
    }

    @Override public void visitPyDelStatement(final PyDelStatement node) {
        ExprVisitor visitor = new ExprVisitor(Operation.Delete);
        for (PyExpression expr: node.getTargets()) {
            expr.accept(visitor);
        }
    }

    @Override public void visitPyExceptBlock(final PyExceptBlock node) {
        PyExpression target = node.getTarget();
        if (target != null) {
            target.accept(new ExprVisitor(Operation.Except));
        }
    }

    @Override public void visitPyForStatement(final PyForStatement node) {
        PyExpression target = node.getTargetExpression();
        if (target != null) {
            target.accept(new ExprVisitor(Operation.For));
        }
    }

    private class ExprVisitor extends PyElementVisitor {
        private Operation _op;

        public ExprVisitor(Operation op) {
            _op = op;
        }

        @Override public void visitPyReferenceExpression(final PyReferenceExpression node) {
            String referencedName = node.getReferencedName();
            if (referencedName != null && referencedName.equals("None")) {
                getHolder().createErrorAnnotation(node,
                        (_op == Operation.Delete) ? "deleting None" : "assignment to None");
            }
        }

        @Override public void visitPyTargetExpression(final PyTargetExpression node) {
            String targetName = node.getName();
            if (targetName != null && targetName.equals("None")) {
                getHolder().createErrorAnnotation(node,
                        (_op == Operation.Delete) ? "deleting None" : "assignment to None");
            }
        }

        @Override public void visitPyCallExpression(final PyCallExpression node) {
            getHolder().createErrorAnnotation(node,
                (_op == Operation.Delete) ? "can't delete function call" : "can't assign to function call");
        }

        @Override public void visitPyGeneratorExpression(final PyGeneratorExpression node) {
            getHolder().createErrorAnnotation(node,
                (_op == Operation.AugAssign)
                        ? "augmented assign to generator expression not possible"
                        : "assign to generator expression not possible");
        }

        @Override public void visitPyBinaryExpression(final PyBinaryExpression node) {
            getHolder().createErrorAnnotation(node, "can't assign to operator");
        }

        @Override public void visitPyTupleExpression(final PyTupleExpression node) {
            if (node.getElements().length == 0) {
                getHolder().createErrorAnnotation(node, "can't assign to ()");
            }
            else if (_op == Operation.AugAssign) {
                getHolder().createErrorAnnotation(node, "augmented assign to tuple literal or generator expression not possible");
            }
            else {
                node.acceptChildren(this);
            }
        }

        @Override public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
            if (_op == Operation.AugAssign) {
                getHolder().createErrorAnnotation(node, "augmented assign to tuple literal or generator expression not possible");
            }
            else {
                node.acceptChildren(this);
            }
        }

        @Override public void visitPyListLiteralExpression(final PyListLiteralExpression node) {
            if (node.getElements().length == 0) {
                getHolder().createErrorAnnotation(node, "can't assign to []");
            }
            else if (_op == Operation.AugAssign) {
                getHolder().createErrorAnnotation(node, "augmented assign to list literal or comprehension not possible");
            }
            else {
                node.acceptChildren(this);
            }
        }

        @Override public void visitPyListCompExpression(final PyListCompExpression node) {
            getHolder().createErrorAnnotation(node,
                    _op == Operation.AugAssign
                            ? "augmented assign to list comprehension not possible"
                            : "can't assign to list comprehension");
        }

        public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
            checkLiteral(node);
        }

        public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
            checkLiteral(node);
        }

        private void checkLiteral(PyExpression node) {
            getHolder().createErrorAnnotation(node, "can't assign to literal");
        }

        public void visitPyLambdaExpression(final PyLambdaExpression node) {
            getHolder().createErrorAnnotation(node, "can't assign to lambda");
        }
    }
}
