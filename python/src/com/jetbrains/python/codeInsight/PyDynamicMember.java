package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class PyDynamicMember {
  private String myName;
  private final boolean myResolveToInstance;
  private final Function<PsiElement, PyType> myTypeCallback;
  private final String myTypeName;

  private ResolveData myResolveData;

  private final PsiElement myTarget;

  public PyDynamicMember(@NotNull final String name, @NotNull final String type, final boolean resolveToInstance) {
    this(name, type, type, resolveToInstance);
  }

  public PyDynamicMember(@NotNull final String name,
                         @NotNull final String type,
                         @NotNull final String resolveTo,
                         final boolean resolveToInstance) {
    myName = name;
    myResolveToInstance = resolveToInstance;
    myTypeName = type;
    myResolveData = ResolveData.createFromPath(resolveTo);

    myTarget = null;
    myTypeCallback = null;
  }

  public PyDynamicMember(@NotNull final String name,
                         @NotNull final String type,
                         @NotNull final String resolveTo,
                         final Function<PsiElement, PyType> typeCallback) {
    myName = name;
    myResolveToInstance = false;
    myTypeName = type;
    myResolveData = ResolveData.createFromPath(resolveTo);

    myTarget = null;
    myTypeCallback = typeCallback;
  }

  public PyDynamicMember(@NotNull final String name, @Nullable final PsiElement target) {
    myName = name;
    myTarget = target;
    myResolveToInstance = false;
    myTypeName = null;
    myResolveData = null;
    myTypeCallback = null;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    if (myTarget != null) {
      return myTarget.getIcon(0);
    }
    return IconLoader.getIcon("/nodes/method.png");
  }

  @Nullable
  public PsiElement resolve(PsiElement context) {
    if (myTarget != null) {
      return myTarget;
    }
    PyClass targetClass = PyClassNameIndex.findClass(myTypeName, context.getProject());
    if (targetClass != null) {
      return new MyInstanceElement(targetClass, context, findResolveTarget(context));
    }
    return null;
  }

  @Nullable
  private PsiElement findResolveTarget(PsiElement context) {
    if (myResolveData == null) {
      return null;
    }
    PsiElement module = ResolveImportUtil.resolveInRoots(context, myResolveData.getModuleName());
    if (module instanceof PsiDirectory) {
      module = PyUtil.turnDirIntoInit(module);
    }
    if (module == null) return null;
    final PyFile file = (PyFile)module;
    for (PyStatement statement : file.getStatements()) {
      final String name = statement.getName();
      if (myResolveData.getShortName().equals(name)) {
        PsiElement searchElement = statement;
        if (myResolveData.getFunctionName()!=null) {
          PsiElement[] funcs = PsiTreeUtil.collectElements(statement, new PsiElementFilter() {
            public boolean isAccepted(PsiElement element) {
              return element instanceof PyFunction && myResolveData.getFunctionName().equals(((PyFunction)element).getName());
            }
          });
          if (funcs.length>0) {
            searchElement = funcs[0];
          }
        }
        if (myResolveData.getCall() != null) {
          PsiElement[] elements = PsiTreeUtil.collectElements(searchElement, new PsiElementFilter() {
            public boolean isAccepted(PsiElement element) {
              PyCallExpression call = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
              return element instanceof PyStringLiteralExpression && call!= null && myResolveData.getCall().equals(call.getCallee().getName()) &&
                     myResolveData.getArg().equals(((PyStringLiteralExpression)element).getStringValue());
            }
          });
          if (elements.length > 0) {
            return elements[0];
          }
        }
        return statement;
      }
    }
    return module;
  }

  @Nullable
  public String getShortType() {
    if (myTypeName == null) {
      return null;
    }
    int pos = myTypeName.lastIndexOf('.');
    return myTypeName.substring(pos + 1);
  }

  private class MyInstanceElement extends PyElementImpl implements PyExpression {
    private final PyClass myClass;
    private final PsiElement myContext;

    public MyInstanceElement(PyClass clazz, PsiElement context, PsiElement resolveTarget) {
      super(resolveTarget != null ? resolveTarget.getNode() : clazz.getNode());
      myClass = clazz;
      myContext = context;
    }

    public PyType getType(@NotNull TypeEvalContext context) {
      if (myTypeCallback != null) {
        return myTypeCallback.fun(myContext);
      }
      return new PyClassType(myClass, !myResolveToInstance);
    }
  }

  static class ResolveData {
    private final String myShortName;
    private final String myModuleName;
    private final String myFunctionName;
    private final String myCall;
    private final String myArg;

    private ResolveData(String shortName, String moduleName, String functionName, String call, String arg) {
      myShortName = shortName;
      myModuleName = moduleName;
      myCall = call;
      myArg = arg;
      myFunctionName = functionName;
    }

    @Nullable
    public static ResolveData createFromPath(@Nullable String resolveTo) {
      if (resolveTo == null) {
        return null;
      }
      String call;
      String arg;
      int ind = resolveTo.indexOf("#");
      if (ind != -1) {
        call = resolveTo.substring(ind + 1);
        if (call.indexOf(",")>0) {
          arg = call.substring(call.indexOf(",") + 1);
          call = call.substring(0, call.indexOf(","));
        } else {
          arg = null;
        }
        resolveTo = resolveTo.substring(0, ind);
      }
      else {
        call = null;
        arg = null;
      }

      int split = resolveTo.lastIndexOf('.');
      String shortName = resolveTo.substring(split + 1);
      String moduleName = resolveTo.substring(0, split);
      split = moduleName.lastIndexOf('.');
      String functionName;
      String s = moduleName.substring(split + 1);
      if (split != -1 && Character.isUpperCase(s.charAt(0))) {
        functionName = shortName;
        shortName = s;
        moduleName = moduleName.substring(0, split);
      } else {
        functionName = null;
      }
      return new ResolveData(shortName, moduleName, functionName, call, arg);
    }

    /**
     * Class or function
     */
    public String getShortName() {
      return myShortName;
    }

    public String getModuleName() {
      return myModuleName;
    }

    public String getCall() {
      return myCall;
    }

    public String getArg() {
      return myArg;
    }

    /**
     * Not null if short name is class
     */
    public String getFunctionName() {
      return myFunctionName;
    }
  }
}
