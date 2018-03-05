/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Custom (aka dynamic) type that delegates calls to some classes you pass to it.
 * We say this this class <strong>mimics</strong> such classes.
 * To be used for cases like "type()".
 * It optionally filters methods using {@link Processor}
 *
 * @author Ilya.Kazakevich
 */
public final class PyCustomType implements PyClassLikeType {

  @NotNull
  private final List<PyClassLikeType> myTypesToMimic;

  @Nullable
  private final Processor<PyElement> myFilter;

  private final boolean myInstanceType;

  private final boolean myTypesToMimicAsSuperTypes;

  @Nullable
  private final String myQualifiedName;


  /**
   * @param filter                   filter to filter methods from classes (may be null to do no filtering)
   * @param instanceType             if true, then this class implements instance (it reports it is not definition and returns "this
   *                                 for {@link #toInstance()} call). If false, <strong>calling this type creates similar type with instance=true</strong>
   *                                 (like ctor)
   * @param typesToMimicAsSuperTypes if true, types to mimic are considered as supertypes
   * @param typesToMimic             types to "mimic": delegate calls to  (must be one at least!)
   */
  public PyCustomType(@Nullable String qualifiedName,
                      @Nullable Processor<PyElement> filter,
                      boolean instanceType,
                      boolean typesToMimicAsSuperTypes,
                      @NotNull PyClassLikeType... typesToMimic) {
    myQualifiedName = qualifiedName;
    myFilter = filter;
    myInstanceType = instanceType;
    myTypesToMimicAsSuperTypes = typesToMimicAsSuperTypes;
    myTypesToMimic = StreamEx
      .of(typesToMimic)
      .nonNull()
      .map(t -> instanceType ? t.toInstance() : t.toClass())
      .toImmutableList();
  }

  /**
   * @return class we mimic (if any). Check class manual for more info.
   */
  @NotNull
  public List<PyClassLikeType> getTypesToMimic() {
    return myTypesToMimic;
  }

  @Override
  public boolean isDefinition() {
    return !myInstanceType;
  }

  @NotNull
  @Override
  public PyClassLikeType toInstance() {
    return myInstanceType
           ? this
           : new PyCustomType(myQualifiedName, myFilter, true, myTypesToMimicAsSuperTypes, myTypesToMimic.toArray(new PyClassLikeType[0]));
  }


  @NotNull
  @Override
  public PyClassLikeType toClass() {
    return myInstanceType
           ? new PyCustomType(myQualifiedName, myFilter, false, myTypesToMimicAsSuperTypes, myTypesToMimic.toArray(new PyClassLikeType[0]))
           : this;
  }

  @Nullable
  @Override
  public String getClassQName() {
    return myQualifiedName;
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    return myTypesToMimicAsSuperTypes ? myTypesToMimic : Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext,
                                                          boolean inherited) {
    final List<RatedResolveResult> globalResult = new ArrayList<>();

    // Delegate calls to classes, we mimic but filter if filter is set.
    for (PyClassLikeType typeToMimic : myTypesToMimic) {
      final List<? extends RatedResolveResult> results = typeToMimic.resolveMember(name, location, direction, resolveContext, inherited);

      if (results != null) {
        globalResult.addAll(Collections2.filter(results, new ResolveFilter()));
      }
    }
    return globalResult;
  }

  @Override
  public boolean isValid() {
    return StreamEx.of(myTypesToMimic).allMatch(PyClassLikeType::isValid);
  }

  @Nullable
  @Override
  public PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited) {
    return null;
  }

  @Override
  public boolean isCallable() {
    return !myInstanceType ||
           PyTypingTypeProvider.CALLABLE.equals(myQualifiedName) ||
           StreamEx.of(myTypesToMimic).anyMatch(PyCallableType::isCallable);
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return myInstanceType ? null : toInstance();
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return getReturnType(context);
  }

  @NotNull
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getAncestorTypes(@NotNull TypeEvalContext context) {
    if (!myTypesToMimicAsSuperTypes) return Collections.emptyList();

    final Set<PyClassLikeType> result = new LinkedHashSet<>();

    for (PyClassLikeType type : myTypesToMimic) {
      result.add(type);
      result.addAll(type.getAncestorTypes(context));
    }

    return new ArrayList<>(result);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<Object> lookupElements = new ArrayList<>();

    for (PyClassLikeType parentType : myTypesToMimic) {
      lookupElements.addAll(Collections2.filter(Arrays.asList(parentType.getCompletionVariants(completionPrefix, location, context)),
                                                new CompletionFilter()));
    }
    return ArrayUtil.toObjectArray(lookupElements);
  }


  @Nullable
  @Override
  public String getName() {
    if (myQualifiedName != null) {
      return QualifiedName.fromDottedString(myQualifiedName).getLastComponent();
    }

    final List<String> classNames = new ArrayList<>(myTypesToMimic.size());
    for (PyClassLikeType type : myTypesToMimic) {
      String name = type.getName();
      if (name == null && type instanceof PyClassType) {
        name = ((PyClassType)type).getPyClass().getName();
      }
      if (name != null) {
        classNames.add(name);
      }
    }


    return PyBundle.message("custom.type.mimic.name", StringUtil.join(classNames, ","));
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
    for (PyClassLikeType type : myTypesToMimic) {
      type.assertValid(message);
    }
  }


  /**
   * Predicate that filters resolve candidates using {@link #myFilter}
   */
  private class ResolveFilter implements Predicate<RatedResolveResult> {
    @Override
    public boolean apply(@Nullable RatedResolveResult input) {
      if (input == null) {
        return false;
      }
      if (myFilter == null) {
        return true; // No need to check
      }
      final PyElement pyElement = PyUtil.as(input.getElement(), PyElement.class);
      if (pyElement == null) {
        return false;
      }
      return myFilter.process(pyElement);
    }
  }

  @Override
  public void visitMembers(@NotNull Processor<PsiElement> processor, boolean inherited, @NotNull TypeEvalContext context) {
    for (PyClassLikeType type : myTypesToMimic) {
      // Only visit methods that are allowed by filter (if any)
      type.visitMembers(t -> {
        if (!(t instanceof PyElement)) {
          return true;
        }
        if (myFilter == null || myFilter.process((PyElement)t)) {
          return processor.process(t);
        }
        return true;
      }, inherited, context);
    }
  }

  @NotNull
  @Override
  public Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    final Set<String> result = new LinkedHashSet<>();

    for (PyClassLikeType type : myTypesToMimic) {
      result.addAll(type.getMemberNames(inherited, context));
    }

    return result;
  }

  /**
   * Predicate that filters completion using {@link #myFilter}
   */
  private class CompletionFilter implements Predicate<Object> {
    @Override
    public boolean apply(@Nullable Object input) {
      if (input == null) {
        return false;
      }
      if (myFilter == null) {
        return true; // No need to check
      }
      if (!(input instanceof LookupElement)) {
        return true; // Do not know how to check
      }
      final PyElement pyElement = PyUtil.as(((LookupElement)input).getPsiElement(), PyElement.class);
      if (pyElement == null) {
        return false;
      }
      return myFilter.process(pyElement);
    }
  }
}
