package com.jetbrains.python.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyPsiPath {
  @Nullable
  public abstract PsiElement resolve(Module module);

  public static class ToFile extends PyPsiPath {
    private final PyQualifiedName myQualifiedName;

    public ToFile(String qualifiedName) {
      myQualifiedName = PyQualifiedName.fromDottedString(qualifiedName);
    }

    @Nullable
    @Override
    public PsiElement resolve(Module module) {
      final List<PsiElement> elements = ResolveImportUtil.resolveModulesInRoots(module, myQualifiedName);
      return elements.size() > 0 ? elements.get(0) : null;
    }
  }

  public static class ToClassQName extends PyPsiPath {
    private final PyQualifiedName myQualifiedName;

    public ToClassQName(String qualifiedName) {
      myQualifiedName = PyQualifiedName.fromDottedString(qualifiedName);
    }

    @Nullable
    @Override
    public PsiElement resolve(Module module) {
      return PyClassNameIndex.findClass(myQualifiedName.toString(), module.getProject());
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
    public PsiElement resolve(Module module) {
      PsiElement parent = myParent.resolve(module);
      if (parent == null) {
        return null;
      }
      if (parent instanceof PyFile) {
        return ((PyFile) parent).findTopLevelClass(myClassName);
      }
      for (PsiElement element : parent.getChildren()) {
        if (element instanceof PyClass && myClassName.equals(((PyClass)element).getName())) {
          return element;
        }
      }
      return parent;
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
    public PsiElement resolve(Module module) {
      PsiElement parent = myParent.resolve(module);
      if (parent == null) {
        return null;
      }
      if (parent instanceof PyFile) {
        return ((PyFile) parent).findTopLevelFunction(myFunctionName);
      }
      if (parent instanceof PyClass) {
        return ((PyClass) parent).findMethodByName(myFunctionName, false);
      }
      for (PsiElement element : parent.getChildren()) {
        if (element instanceof PyFunction && myFunctionName.equals(((PyFunction)element).getName())) {
          return element;
        }
      }
      return parent;
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
    public PsiElement resolve(Module module) {
      PsiElement parent = myParent.resolve(module);
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
}
