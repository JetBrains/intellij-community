package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyElementImpl;
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

  private final PsiElement myTarget;
  private PyPsiPath myPsiPath;

  public PyDynamicMember(@NotNull final String name, @NotNull final String type, final boolean resolveToInstance) {
    myName = name;
    myResolveToInstance = resolveToInstance;
    myTypeName = type;

    myTarget = null;
    myTypeCallback = null;
  }

  public PyDynamicMember(@NotNull final String name) {
    myName = name;
    myResolveToInstance = false;
    myTypeName = null;

    myTarget = null;
    myTypeCallback = null;
  }

  public PyDynamicMember(@NotNull final String name,
                         @NotNull final String type,
                         final Function<PsiElement, PyType> typeCallback) {
    myName = name;

    myResolveToInstance = false;
    myTypeName = type;

    myTarget = null;
    myTypeCallback = typeCallback;
  }

  public PyDynamicMember(@NotNull final String name, @Nullable final PsiElement target) {
    myName = name;
    myTarget = target;
    myResolveToInstance = false;
    myTypeName = null;
    myTypeCallback = null;
  }

  public PyDynamicMember resolvesTo(String moduleQName) {
    myPsiPath = new PyPsiPath.ToFile(moduleQName);
    return this;
  }

  public PyDynamicMember resolvesToClass(String classQName) {
    myPsiPath = new PyPsiPath.ToClassQName(classQName);
    return this;
  }

  public PyDynamicMember toClass(String name) {
    myPsiPath = new PyPsiPath.ToClass(myPsiPath, name);
    return this;
  }

  public PyDynamicMember toFunction(String name) {
    myPsiPath = new PyPsiPath.ToFunction(myPsiPath, name);
    return this;
  }

  public PyDynamicMember toClassAttribute(String name) {
    myPsiPath = new PyPsiPath.ToClassAttribute(myPsiPath, name);
    return this;
  }

  public PyDynamicMember toCall(String name, String... args) {
    myPsiPath = new PyPsiPath.ToCall(myPsiPath, name, args);
    return this;
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
    PyClass targetClass = myTypeName != null && myTypeName.indexOf('.') > 0 ? PyClassNameIndex.findClass(myTypeName, context.getProject()) : null;
    final PsiElement resolveTarget = findResolveTarget(context);
    if (resolveTarget instanceof PyFunction) {
      return resolveTarget;
    }
    if (resolveTarget != null || targetClass != null) {
      return new MyInstanceElement(targetClass, context, resolveTarget);
    }
    return null;
  }

  @Nullable
  private PsiElement findResolveTarget(PsiElement context) {
    if (myPsiPath != null) {
      return myPsiPath.resolve(context);
    }
    return null;
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
}
