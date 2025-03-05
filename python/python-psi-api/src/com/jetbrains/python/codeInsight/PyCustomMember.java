// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * Note: if you use {@link #myTypeName} to override real field, be sure to use
 * {@link com.jetbrains.python.psi.types.PyOverridingClassMembersProvider}
 *
 * @author Dennis.Ushakov
 */
public class PyCustomMember extends UserDataHolderBase {
  private static final Logger LOG = Logger.getInstance(PyCustomMember.class);
  private static final Key<ParameterizedCachedValue<PyClass, PsiElement>>
    RESOLVE = Key.create("resolve");
  private final String myName;
  private final boolean myResolveToInstance;
  private final Function<? super PsiElement, ? extends PyType> myTypeCallback;
  private final @Nullable String myTypeName;

  private final PsiElement myTarget;
  private PyPsiPath myPsiPath;

  boolean myFunction = false;

  /**
   * Force resolving to {@link com.jetbrains.python.codeInsight.PyCustomMemberProviderImpl.MyInstanceElement} even if element is function
   */
  private boolean myAlwaysResolveToCustomElement;
  private Icon myIcon = AllIcons.Nodes.Method;
  private PyCustomMemberTypeInfo<?> myCustomTypeInfo;

  public PyCustomMember(final @NotNull String name, final @Nullable String type, final boolean resolveToInstance) {
    myName = name;
    myResolveToInstance = resolveToInstance;
    myTypeName = type;

    myTarget = null;
    myTypeCallback = null;
  }

  public PyCustomMember(final @NotNull String name) {
    myName = name;
    myResolveToInstance = false;
    myTypeName = null;

    myTarget = null;
    myTypeCallback = null;
  }

  public PyCustomMember(final @NotNull String name,
                        final @Nullable String type,
                        final Function<? super PsiElement, ? extends PyType> typeCallback) {
    myName = name;

    myResolveToInstance = false;
    myTypeName = type;

    myTarget = null;
    myTypeCallback = typeCallback;
  }

  public PyCustomMember(final @NotNull String name, final @Nullable PsiElement target, @Nullable String typeName) {
    myName = name;
    myTarget = target;
    myResolveToInstance = false;
    myTypeName = typeName;
    myTypeCallback = null;
  }

  public PyCustomMember(final @NotNull String name, final @Nullable PsiElement target) {
    this(name, target, null);
  }

  public PyCustomMember resolvesTo(String moduleQName) {
    myPsiPath = new PyPsiPath.ToFile(moduleQName);
    return this;
  }

  public PyCustomMember resolvesToClass(final @NotNull String classQName) {
    myPsiPath = new PyPsiPath.ToClassQName(classQName);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyCustomMember member = (PyCustomMember)o;
    return myResolveToInstance == member.myResolveToInstance &&
           myFunction == member.myFunction &&
           myAlwaysResolveToCustomElement == member.myAlwaysResolveToCustomElement &&
           Objects.equals(myName, member.myName) &&
           Objects.equals(myTypeCallback, member.myTypeCallback) &&
           Objects.equals(myTypeName, member.myTypeName) &&
           Objects.equals(myTarget, member.myTarget) &&
           Objects.equals(myPsiPath, member.myPsiPath) &&
           Objects.equals(myCustomTypeInfo, member.myCustomTypeInfo);
  }

  @Override
  public int hashCode() {
    return Objects
      .hash(myName, myResolveToInstance, myTypeCallback, myTypeName, myTarget, myPsiPath, myFunction, myAlwaysResolveToCustomElement,
            myCustomTypeInfo);
  }

  /**
   * Force resolving to {@link com.jetbrains.python.codeInsight.PyCustomMemberProviderImpl.MyInstanceElement} even if element is function
   */
  public final @NotNull PyCustomMember alwaysResolveToCustomElement() {
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
      public PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
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

  /**
   * Resolves custom member in specified context.
   *
   * @param context        psi element to be used as psi context
   * @param resolveContext context to be used in resolve
   * @return resolved element
   */
  public @Nullable PsiElement resolve(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {

    if (myTarget != null) {
      return myTarget;
    }

    PyClass targetClass = null;
    if (myTypeName != null) {

      final ParameterizedCachedValueProvider<PyClass, PsiElement> provider = param -> {
        final PyClass result = PyPsiFacade.getInstance(param.getProject()).createClassByQName(myTypeName, param);
        return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      };
      targetClass = CachedValuesManager.getManager(context.getProject()).getParameterizedCachedValue(this, RESOLVE,
                                                                                                     provider, false, context);
    }
    final PsiElement resolveTarget = findResolveTarget(context, resolveContext);
    if (resolveTarget instanceof PyFunction && !myAlwaysResolveToCustomElement) {
      return resolveTarget;
    }
    if (resolveTarget != null || targetClass != null) {
      if (targetClass == null && resolveTarget instanceof PyClass) {
        targetClass = (PyClass)resolveTarget;
      }

      return PyCustomMemberProvider.getInstance().createPyCustomMemberTarget(this, targetClass, context, resolveTarget, myTypeCallback, myCustomTypeInfo, myResolveToInstance);
    }
    return null;
  }

  private @Nullable PsiElement findResolveTarget(@NotNull PsiElement context, @NotNull PyResolveContext resolveContext) {
    if (myPsiPath != null) {
      return myPsiPath.resolve(context, resolveContext);
    }
    return null;
  }

  public @Nullable String getShortType() {
    if (myTypeName == null) {
      return null;
    }
    final int pos = myTypeName.lastIndexOf('.');
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
  public final boolean isReferenceToMe(final @NotNull PsiReference reference) {
    return PyCustomMemberProvider.getInstance().isReferenceToMe(reference, this);
  }

  /**
   * @param icon icon to use (will be used method icon otherwise)
   */
  public PyCustomMember withIcon(final @NotNull Icon icon) {
    myIcon = icon;
    return this;
  }

  /**
   * Adds custom info to type if class has {@link #myTypeName} set.
   * Info could be later obtained by key.
   *
   * @param customInfo custom info to add
   */
  public PyCustomMember withCustomTypeInfo(final @NotNull PyCustomMemberTypeInfo<?> customInfo) {
    LOG.assertTrue(myTypeName != null, "Cant add custom type info if no type provided");
    myCustomTypeInfo = customInfo;
    return this;
  }

}
