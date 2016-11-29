/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
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
public class PyCustomType implements PyClassLikeType {

  @NotNull
  private final List<PyClassLikeType> myTypesToMimic = new ArrayList<>();

  @Nullable
  private final Processor<PyElement> myFilter;

  private final boolean myInstanceType;


  /**
   * @param filter       filter to filter methods from classes (may be null to do no filtering)
   * @param instanceType if true, then this class implements instance (it reports it is not definition and returns "this
   *                     for {@link #toInstance()} call). If false, <strong>calling this type creates similar type with instance=true</strong>
   *                     (like ctor)
   * @param typesToMimic types to "mimic": delegate calls to  (must be one at least!)
   */
  public PyCustomType(@Nullable final Processor<PyElement> filter,
                      final boolean instanceType,
                      @NotNull final PyClassLikeType... typesToMimic) {
    Preconditions.checkArgument(typesToMimic.length > 0, "Provide at least one class");
    myFilter = filter;
    myTypesToMimic.addAll(Collections2.filter(Arrays.asList(typesToMimic), NotNullPredicate.INSTANCE));
    myInstanceType = instanceType;
  }

  /**
   * @return class we mimic (if any). Check class manual for more info.
   */
  @NotNull
  public final List<PyClassLikeType> getTypesToMimic() {
    return Collections.unmodifiableList(myTypesToMimic);
  }

  @Override
  public final boolean isDefinition() {
    return !myInstanceType;
  }

  @Override
  public final PyClassLikeType toInstance() {
    return myInstanceType
           ? this
           : new PyCustomType(myFilter, true, myTypesToMimic.toArray(new PyClassLikeType[myTypesToMimic.size()]));
  }


  @Nullable
  @Override
  public final String getClassQName() {
    return null;
  }

  @NotNull
  @Override
  public final List<PyClassLikeType> getSuperClassTypes(@NotNull final TypeEvalContext context) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public final List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                                @Nullable final PyExpression location,
                                                                @NotNull final AccessDirection direction,
                                                                @NotNull final PyResolveContext resolveContext,
                                                                final boolean inherited) {
    final List<RatedResolveResult> globalResult = new ArrayList<>();

    // Delegate calls to classes, we mimic but filter if filter is set.
    for (final PyClassLikeType typeToMimic : myTypesToMimic) {
      final List<? extends RatedResolveResult> results = typeToMimic.toInstance().resolveMember(
        name, location, direction, resolveContext, inherited
      );

      if (results != null) {
        globalResult.addAll(Collections2.filter(results, new ResolveFilter()));
      }
    }
    return globalResult;
  }

  @Override
  public final boolean isValid() {
    for (final PyClassLikeType type : myTypesToMimic) {
      if (!type.isValid()) {
        return false;
      }
    }

    return true;
  }

  @Nullable
  @Override
  public final PyClassLikeType getMetaClassType(@NotNull final TypeEvalContext context, final boolean inherited) {
    return null;
  }

  @Override
  public final boolean isCallable() {
    if (!myInstanceType) {
      return true; // Due to ctor
    }
    for (final PyClassLikeType typeToMimic : myTypesToMimic) {
      if (typeToMimic.isCallable()) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  @Override
  public final PyType getReturnType(@NotNull final TypeEvalContext context) {
    return (myInstanceType ? null : toInstance());
  }

  @Nullable
  @Override
  public final PyType getCallType(@NotNull final TypeEvalContext context, @NotNull final PyCallSiteExpression callSite) {
    return getReturnType(context);
  }

  @Nullable
  @Override
  public final List<PyCallableParameter> getParameters(@NotNull final TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public final List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                                @Nullable final PyExpression location,
                                                                @NotNull final AccessDirection direction,
                                                                @NotNull final PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @NotNull
  @Override
  public final List<PyClassLikeType> getAncestorTypes(@NotNull final TypeEvalContext context) {
    final Collection<PyClassLikeType> result = new LinkedHashSet<>();
    for (final PyClassLikeType type : myTypesToMimic) {
      result.addAll(type.getAncestorTypes(context));
    }

    return new ArrayList<>(result);
  }

  @Override
  public final Object[] getCompletionVariants(final String completionPrefix, final PsiElement location, final ProcessingContext context) {
    final Collection<Object> lookupElements = new ArrayList<>();

    for (final PyClassLikeType parentType : myTypesToMimic) {
      lookupElements.addAll(Collections2.filter(Arrays.asList(parentType.getCompletionVariants(completionPrefix, location, context)),
                                                new CompletionFilter()));
    }
    return lookupElements.toArray(new Object[lookupElements.size()]);
  }


  @Nullable
  @Override
  public final String getName() {
    final Collection<String> classNames = new ArrayList<>(myTypesToMimic.size());
    for (final PyClassLikeType type : myTypesToMimic) {
      String name = type.getName();
      if (name == null && (type instanceof PyClassType)) {
        name = ((PyClassType)type).getPyClass().getName();
      }
      if (name != null) {
        classNames.add(name);
      }
    }


    return PyBundle.message("custom.type.mimic.name", StringUtil.join(classNames, ","));
  }

  @Override
  public final boolean isBuiltin() {
    return false;
  }

  @Override
  public final void assertValid(final String message) {
    for (final PyClassLikeType type : myTypesToMimic) {
      type.assertValid(message);
    }
  }


  /**
   * Predicate that filters resolve candidates using {@link #myFilter}
   */
  private class ResolveFilter implements Predicate<RatedResolveResult> {
    @Override
    public final boolean apply(@Nullable final RatedResolveResult input) {
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
  public final void visitMembers(@NotNull final Processor<PsiElement> processor,
                                 final boolean inherited,
                                 @NotNull final TypeEvalContext context) {
    for (final PyClassLikeType type : myTypesToMimic) {
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
    public final boolean apply(@Nullable final Object input) {
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
