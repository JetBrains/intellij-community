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
package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class PyPsiPath {
  @Nullable
  public abstract PsiElement resolve(PsiElement module);

  public static class ToFile extends PyPsiPath {
    private final QualifiedName myQualifiedName;

    public ToFile(String qualifiedName) {
      myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    }

    @Nullable
    @Override
    public PsiElement resolve(PsiElement context) {
      PyPsiFacade pyPsiFacade = PyPsiFacade.getInstance(context.getProject());
      QualifiedNameResolver visitor = pyPsiFacade.qualifiedNameResolver(myQualifiedName).fromElement(context);
      return visitor.firstResult();
    }
  }

  public static class ToClassQName extends PyPsiPath {
    private final QualifiedName myQualifiedName;

    public ToClassQName(String qualifiedName) {
      myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    }

    @Nullable
    @Override
    public PsiElement resolve(PsiElement context) {
      return PyPsiFacade.getInstance(context.getProject()).findClass(myQualifiedName.toString());
    }
  }

  public static class ToClass extends PyPsiPath {
    private final PyPsiPath myParent;
    private final String myClassName;

    public ToClass(PyPsiPath parent, String className) {
      myParent = parent;
      myClassName = className;
    }

    @Override
    public PsiElement resolve(PsiElement context) {
      PsiElement parent = myParent.resolve(context);
      if (parent == null) {
        return null;
      }
      if (parent instanceof PyFile) {
        return ((PyFile) parent).findTopLevelClass(myClassName);
      }
      if (parent instanceof PyClass) {
        for (PsiElement element : parent.getChildren()) {
          if (element instanceof PyClass && myClassName.equals(((PyClass)element).getName())) {
            return element;
          }
        }
      }
      ClassFinder finder = new ClassFinder(myClassName);
      parent.acceptChildren(finder);
      return finder.myResult != null ? finder.myResult : parent;
    }
  }

  private static class ClassFinder extends PyRecursiveElementVisitor {
    private final String myName;
    private PyClass myResult;

    public ClassFinder(String name) {
      myName = name;
    }

    @Override
    public void visitPyClass(PyClass node) {
      super.visitPyClass(node);
      if (myName.equals(node.getName())) {
        myResult = node;
      }
    }
  }

  public static class ToFunction extends PyPsiPath {
    private final PyPsiPath myParent;
    private final String myFunctionName;

    public ToFunction(PyPsiPath parent, String functionName) {
      myParent = parent;
      myFunctionName = functionName;
    }

    @Override
    public PsiElement resolve(PsiElement context) {
      PsiElement parent = myParent.resolve(context);
      if (parent == null) {
        return null;
      }
      if (parent instanceof PyFile) {
        return ((PyFile) parent).findTopLevelFunction(myFunctionName);
      }
      if (parent instanceof PyClass) {
        return ((PyClass) parent).findMethodByName(myFunctionName, false, null);
      }
      for (PsiElement element : parent.getChildren()) {
        if (element instanceof PyFunction && myFunctionName.equals(((PyFunction)element).getName())) {
          return element;
        }
      }
      return parent;
    }
  }

  private static class FunctionFinder extends PyRecursiveElementVisitor {
    private final String myName;
    private PyFunction myResult;

    public FunctionFinder(String name) {
      myName = name;
    }

    @Override
    public void visitPyFunction(PyFunction node) {
      super.visitPyFunction(node);
      if (myName.equals(node.getName())) {
        myResult = node;
      }
    }
  }

  public static class ToFunctionRecursive extends PyPsiPath {
    private final PyPsiPath myParent;
    private final String myFunctionName;

    public ToFunctionRecursive(PyPsiPath parent, String functionName) {
      myParent = parent;
      myFunctionName = functionName;
    }

    @Override
    public PsiElement resolve(PsiElement context) {
      PsiElement parent = myParent.resolve(context);
      if (parent == null) {
        return null;
      }
      FunctionFinder finder = new FunctionFinder(myFunctionName);
      parent.acceptChildren(finder);
      return finder.myResult != null ? finder.myResult : parent;
    }
  }

  public static class ToClassAttribute extends PyPsiPath {
    private final PyPsiPath myParent;
    private final String myAttributeName;

    public ToClassAttribute(PyPsiPath parent, String attributeName) {
      myAttributeName = attributeName;
      myParent = parent;
    }

    @Override
    public PsiElement resolve(PsiElement context) {
      PsiElement parent = myParent.resolve(context);
      if (!(parent instanceof PyClass)) {
        return null;
      }
      return ((PyClass)parent).findClassAttribute(myAttributeName, true, null);
    }
  }

  public static class ToCall extends PyPsiPath {
    private final PyPsiPath myParent;
    private final String myCallName;
    private final String[] myArgs;

    public ToCall(PyPsiPath parent, String callName, String... args) {
      myParent = parent;
      myCallName = callName;
      myArgs = args;
    }

    @Override
    public PsiElement resolve(PsiElement context) {
      PsiElement parent = myParent.resolve(context);
      if (parent == null) {
        return null;
      }
      CallFinder finder = new CallFinder(myCallName, myArgs);
      parent.accept(finder);
      return finder.myResult != null ? finder.myResult : parent;
    }
  }

  private static class CallFinder extends PyRecursiveElementVisitor {
    private PsiElement myResult;
    private final String myCallName;
    private final String[] myArgs;

    public CallFinder(String callName, String[] args) {
      myCallName = callName;
      myArgs = args;
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if (myResult != null) {
        return;
      }
      super.visitPyCallExpression(node);

      final PyExpression callee = node.getCallee();
      if (callee instanceof PyReferenceExpression) {
        String calleeName = ((PyReferenceExpression) callee).getReferencedName();
        if (myCallName.equals(calleeName)) {
          final PyExpression[] args = node.getArguments();
          if (myArgs.length <= args.length) {
            boolean argsMatch = true;
            for (int i = 0; i < myArgs.length; i++) {
              if (!(args[i] instanceof PyStringLiteralExpression) ||
                  !myArgs [i].equals(((PyStringLiteralExpression)args[i]).getStringValue())) {
                argsMatch = false;
                break;
              }
            }
            if (argsMatch) {
              myResult = node;
            }
          }
        }
      }
    }
  }

  public static class ToAssignment extends PyPsiPath {
    private final PyPsiPath myParent;
    private final String myAssignee;

    public ToAssignment(PyPsiPath parent, String assignee) {
      myParent = parent;
      myAssignee = assignee;
    }

    @Nullable
    @Override
    public PsiElement resolve(PsiElement context) {
      PsiElement parent = myParent.resolve(context);
      if (parent == null) {
        return null;
      }
      AssignmentFinder finder = new AssignmentFinder(myAssignee);
      parent.accept(finder);
      return finder.myResult != null ? finder.myResult : parent;
    }
  }

  private static class AssignmentFinder extends PyRecursiveElementVisitor {
    private final String myAssignee;
    private PsiElement myResult;

    public AssignmentFinder(String assignee) {
      myAssignee = assignee;
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      PyExpression lhs = node.getLeftHandSideExpression();
      if (lhs != null && myAssignee.equals(lhs.getText())) {
        myResult = node;
      }
    }
  }
}
