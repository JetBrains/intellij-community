// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class PyCustomMemberProviderImpl extends PyCustomMemberProvider {
  @Override
  public PyTypedElement createPyCustomMemberTarget(PyCustomMember member,
                                                   PyClass clazz,
                                                   PsiElement context,
                                                   PsiElement resolveTarget,
                                                   Function<? super PsiElement, ? extends PyType> typeCallback,
                                                   PyCustomMemberTypeInfo<?> customTypeInfo, boolean resolveToInstance) {
    return new MyInstanceElement(member, clazz, context, resolveTarget, typeCallback, customTypeInfo, resolveToInstance);
  }

  @Override
  public boolean isReferenceToMe(PsiReference reference, PyCustomMember member) {
    final PsiElement element = reference.resolve();
    if (!(element instanceof MyInstanceElement)) {
      return false;
    }
    return ((MyInstanceElement)element).getThis().equals(member);
  }

  private static class MyInstanceElement extends ASTWrapperPsiElement implements PyTypedElement {
    private final PyCustomMember myMember;
    private final PyClass myClass;
    private final PsiElement myContext;
    private final Function<? super PsiElement, ? extends PyType> myTypeCallback;
    private final PyCustomMemberTypeInfo<?> myCustomTypeInfo;
    private final boolean myResolveToInstance;

    MyInstanceElement(PyCustomMember member,
                      PyClass clazz,
                      PsiElement context,
                      PsiElement resolveTarget,
                      Function<? super PsiElement, ? extends PyType> typeCallback,
                      PyCustomMemberTypeInfo<?> customTypeInfo, boolean resolveToInstance) {
      super(resolveTarget != null ? resolveTarget.getNode() : clazz.getNode());
      myMember = member;
      myClass = clazz;
      myContext = context;
      myTypeCallback = typeCallback;
      myCustomTypeInfo = customTypeInfo;
      myResolveToInstance = resolveToInstance;
    }

    private PyCustomMember getThis() {
      return myMember;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyInstanceElement element = (MyInstanceElement)o;
      return Objects.equals(getThis(), element.getThis()) &&
             Objects.equals(myClass, element.myClass) &&
             Objects.equals(myContext, element.myContext) &&
             Objects.equals(getNode(), element.getNode());
    }

    @Override
    public String toString() {
      return "MyInstanceElement{" +
             "myClass=" + myClass +
             "member=" + getThis() +
             "node=" + getNode() +
             ", myContext=" + myContext +
             '}';
    }

    @Override
    public int hashCode() {
      return Objects.hash(myClass, myContext, getNode(), getThis());
    }

    @Override
    @Nullable
    public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
      if (myTypeCallback != null) {
        return myTypeCallback.fun(myContext);
      }
      else if (myClass != null) {
        final PyClassType type = PyPsiFacade.getInstance(getProject()).createClassType(myClass, !myResolveToInstance);
        if (myCustomTypeInfo != null) {
          myCustomTypeInfo.fill(type);
        }
        return type;
      }
      return null;
    }
  }

}
