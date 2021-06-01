// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class PyPsiPath {

  /**
   * Resolves psi path in specified context.
   *
   * @param context        psi element to be used as psi context
   * @param resolveContext context to be used in resolve
   * @return resolved element
   */
  @Nullable
  public abstract PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext);

  public static class ToFile extends PyPsiPath {
    private final QualifiedName myQualifiedName;

    public ToFile(String qualifiedName) {
      myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    }

    @Nullable
    @Override
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PyPsiFacade facade = PyPsiFacade.getInstance(context.getProject());
      return facade.resolveQualifiedName(myQualifiedName, facade.createResolveContextFromFoothold(context))
        .stream().findFirst().orElse(null);
    }
  }

  public static class ToClassQName extends PyPsiPath {
    private final QualifiedName myQualifiedName;

    public ToClassQName(@NotNull final String qualifiedName) {
      myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    }

    @Nullable
    @Override
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
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
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PsiElement parent = myParent.resolve(context, resolveContext);
      if (parent == null) {
        return null;
      }
      if (parent instanceof PyFile) {
        return ((PyFile) parent).findTopLevelClass(myClassName);
      }
      if (resolveContext.getTypeEvalContext().maySwitchToAST(parent)) {
        if (parent instanceof PyClass) {
          for (PsiElement element : parent.getChildren()) {
            if (element instanceof PyClass && myClassName.equals(((PyClass)element).getName())) {
              return element;
            }
          }
        }
        final ClassFinder finder = new ClassFinder(myClassName);
        parent.acceptChildren(finder);
        return finder.myResult != null ? finder.myResult : parent;
      }
      return parent;
    }
  }

  private static class ClassFinder extends PyRecursiveElementVisitor {
    private final String myName;
    private PyClass myResult;

    ClassFinder(String name) {
      myName = name;
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
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
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PsiElement parent = myParent.resolve(context, resolveContext);
      if (parent == null) {
        return null;
      }
      if (parent instanceof PyFile) {
        return ((PyFile) parent).findTopLevelFunction(myFunctionName);
      }
      if (parent instanceof PyClass) {
        return ((PyClass) parent).findMethodByName(myFunctionName, false, resolveContext.getTypeEvalContext());
      }
      if (resolveContext.getTypeEvalContext().maySwitchToAST(parent)) {
        for (PsiElement element : parent.getChildren()) {
          if (element instanceof PyFunction && myFunctionName.equals(((PyFunction)element).getName())) {
            return element;
          }
        }
      }
      return parent;
    }
  }

  private static class FunctionFinder extends PyRecursiveElementVisitor {
    private final String myName;
    private PyFunction myResult;

    FunctionFinder(String name) {
      myName = name;
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
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
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PsiElement parent = myParent.resolve(context, resolveContext);
      if (parent == null || !resolveContext.getTypeEvalContext().maySwitchToAST(parent)) {
        return null;
      }
      final FunctionFinder finder = new FunctionFinder(myFunctionName);
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
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PsiElement parent = myParent.resolve(context, resolveContext);
      if (!(parent instanceof PyClass)) {
        return null;
      }
      return ((PyClass)parent).findClassAttribute(myAttributeName, true, resolveContext.getTypeEvalContext());
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
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PsiElement parent = myParent.resolve(context, resolveContext);
      if (parent == null || !resolveContext.getTypeEvalContext().maySwitchToAST(parent)) {
        return null;
      }
      final CallFinder finder = new CallFinder(myCallName, myArgs);
      parent.accept(finder);
      return finder.myResult != null ? finder.myResult : parent;
    }
  }

  private static class CallFinder extends PyRecursiveElementVisitor {
    private PsiElement myResult;
    private final String myCallName;
    private final String[] myArgs;

    CallFinder(String callName, String[] args) {
      myCallName = callName;
      myArgs = args;
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      if (myResult != null) {
        return;
      }
      super.visitPyCallExpression(node);

      final PyExpression callee = node.getCallee();
      if (callee instanceof PyReferenceExpression) {
        final String calleeName = ((PyReferenceExpression) callee).getReferencedName();
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
    public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
      final PsiElement parent = myParent.resolve(context, resolveContext);
      if (parent == null || !resolveContext.getTypeEvalContext().maySwitchToAST(parent)) {
        return null;
      }
      final AssignmentFinder finder = new AssignmentFinder(myAssignee);
      parent.accept(finder);
      return finder.myResult != null ? finder.myResult : parent;
    }
  }

  private static class AssignmentFinder extends PyRecursiveElementVisitor {
    private final String myAssignee;
    private PsiElement myResult;

    AssignmentFinder(String assignee) {
      myAssignee = assignee;
    }

    @Override
    public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
      final PyExpression lhs = node.getLeftHandSideExpression();
      if (lhs != null && myAssignee.equals(lhs.getText())) {
        myResult = node;
      }
    }
  }
}
