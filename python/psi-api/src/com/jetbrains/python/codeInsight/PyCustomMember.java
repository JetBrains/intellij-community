/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.common.base.Preconditions;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Note: if you use {@link #myTypeName} to override real field, be sure to use
 * {@link com.jetbrains.python.psi.types.PyOverridingClassMembersProvider}
 *
 * @author Dennis.Ushakov
 */
public class PyCustomMember extends UserDataHolderBase {
  private static final Key<ParameterizedCachedValue<PyClass, PsiElement>>
    RESOLVE = Key.create("resolve");
  private final String myName;
  private final boolean myResolveToInstance;
  private final Function<PsiElement, PyType> myTypeCallback;
  @Nullable
  private final String myTypeName;

  private final PsiElement myTarget;
  private PyPsiPath myPsiPath;

  boolean myFunction = false;

  /**
   * Force resolving to {@link MyInstanceElement} even if element is function
   */
  private boolean myAlwaysResolveToCustomElement;
  private Icon myIcon = AllIcons.Nodes.Method;
  private PyCustomMemberTypeInfo<?> myCustomTypeInfo;

  public PyCustomMember(@NotNull final String name, @Nullable final String type, final boolean resolveToInstance) {
    myName = name;
    myResolveToInstance = resolveToInstance;
    myTypeName = type;

    myTarget = null;
    myTypeCallback = null;
  }

  public PyCustomMember(@NotNull final String name) {
    myName = name;
    myResolveToInstance = false;
    myTypeName = null;

    myTarget = null;
    myTypeCallback = null;
  }

  public PyCustomMember(@NotNull final String name,
                        @Nullable final String type,
                        final Function<PsiElement, PyType> typeCallback) {
    myName = name;

    myResolveToInstance = false;
    myTypeName = type;

    myTarget = null;
    myTypeCallback = typeCallback;
  }

  public PyCustomMember(@NotNull final String name, @Nullable final PsiElement target, @Nullable String typeName) {
    myName = name;
    myTarget = target;
    myResolveToInstance = false;
    myTypeName = typeName;
    myTypeCallback = null;
  }

  public PyCustomMember(@NotNull final String name, @Nullable final PsiElement target) {
    this(name, target, null);
  }

  public PyCustomMember resolvesTo(String moduleQName) {
    myPsiPath = new PyPsiPath.ToFile(moduleQName);
    return this;
  }

  public PyCustomMember resolvesToClass(String classQName) {
    myPsiPath = new PyPsiPath.ToClassQName(classQName);
    return this;
  }

  /**
   * Force resolving to {@link MyInstanceElement} even if element is function
   */
  @NotNull
  public final PyCustomMember alwaysResolveToCustomElement() {
    myAlwaysResolveToCustomElement = true;
    return this;
  }

  public PyCustomMember toClass(String name) {
    myPsiPath = new PyPsiPath.ToClass(myPsiPath, name);
    return this;
  }

  public PyCustomMember toFunction(String name) {
    myPsiPath = new PyPsiPath.ToFunction(myPsiPath, name);
    return this;
  }

  public PyCustomMember toFunctionRecursive(String name) {
    myPsiPath = new PyPsiPath.ToFunctionRecursive(myPsiPath, name);
    return this;
  }

  public PyCustomMember toClassAttribute(String name) {
    myPsiPath = new PyPsiPath.ToClassAttribute(myPsiPath, name);
    return this;
  }

  public PyCustomMember toCall(String name, String... args) {
    myPsiPath = new PyPsiPath.ToCall(myPsiPath, name, args);
    return this;
  }

  public PyCustomMember toAssignment(String assignee) {
    myPsiPath = new PyPsiPath.ToAssignment(myPsiPath, assignee);
    return this;
  }

  public PyCustomMember toPsiElement(final PsiElement psiElement) {
    myPsiPath = new PyPsiPath() {

      @Override
      public PsiElement resolve(PsiElement module) {
        return psiElement;
      }
    };
    return this;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    if (myTarget != null) {
      return myTarget.getIcon(0);
    }
    return myIcon;
  }

  @Nullable
  public PsiElement resolve(@NotNull final PsiElement context) {

    if (myTarget != null) {
      return myTarget;
    }

    PyClass targetClass = null;
    if (myTypeName != null) {

      final ParameterizedCachedValueProvider<PyClass, PsiElement> provider = new ParameterizedCachedValueProvider<PyClass, PsiElement>() {
        @Nullable
        @Override
        public CachedValueProvider.Result<PyClass> compute(
          final PsiElement param) {
          final PyClass result = PyPsiFacade.getInstance(param.getProject()).createClassByQName(myTypeName, param);
          return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
        }
      };
      targetClass = CachedValuesManager.getManager(context.getProject()).getParameterizedCachedValue(this, RESOLVE,
                                                                                                     provider, false, context);
    }
    final PsiElement resolveTarget = findResolveTarget(context);
    if (resolveTarget instanceof PyFunction && !myAlwaysResolveToCustomElement) {
      return resolveTarget;
    }
    if (resolveTarget != null || targetClass != null) {
      return new MyInstanceElement(targetClass, context, resolveTarget);
    }
    return null;
  }

  @Nullable
  private PsiElement findResolveTarget(@NotNull PsiElement context) {
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

  public PyCustomMember asFunction() {
    myFunction = true;
    return this;
  }

  public boolean isFunction() {
    return myFunction;
  }

  /**
   * Checks if some reference points to this element
   *
   * @param reference reference to check
   * @return true if reference points to it
   */
  public final boolean isReferenceToMe(@NotNull final PsiReference reference) {
    final PsiElement element = reference.resolve();
    if (!(element instanceof MyInstanceElement)) {
      return false;
    }
    return ((MyInstanceElement)element).getThis().equals(this);
  }

  /**
   * @param icon icon to use (will be used method icon otherwise)
   */
  public PyCustomMember withIcon(@NotNull final Icon icon) {
    myIcon = icon;
    return this;
  }

  /**
   * Adds custom info to type if class has {@link #myTypeName} set.
   * Info could be later obtained by key.
   *
   * @param customInfo custom info to add
   */
  public PyCustomMember withCustomTypeInfo(@NotNull final PyCustomMemberTypeInfo<?> customInfo) {
    Preconditions.checkState(myTypeName != null, "Cant add custom type info if no type provided");
    myCustomTypeInfo = customInfo;
    return this;
  }

  private class MyInstanceElement extends ASTWrapperPsiElement implements PyTypedElement {
    private final PyClass myClass;
    private final PsiElement myContext;

    public MyInstanceElement(PyClass clazz, PsiElement context, PsiElement resolveTarget) {
      super(resolveTarget != null ? resolveTarget.getNode() : clazz.getNode());
      myClass = clazz;
      myContext = context;
    }

    private PyCustomMember getThis() {
      return PyCustomMember.this;
    }

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