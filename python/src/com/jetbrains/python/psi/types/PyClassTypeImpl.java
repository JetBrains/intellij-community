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
package com.jetbrains.python.psi.types;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PyCustomMemberUtils;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyResolveResultRater;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyClassTypeImpl extends UserDataHolderBase implements PyClassType {

  @NotNull protected final PyClass myClass;
  protected final boolean myIsDefinition;

  private static ThreadLocal<Set<Pair<PyClass, String>>> ourResolveMemberStack = new ThreadLocal<Set<Pair<PyClass, String>>>() {
    @Override
    protected Set<Pair<PyClass, String>> initialValue() {
      return new HashSet<>();
    }
  };

  /**
   * Describes a class-based type. Since everything in Python is an instance of some class, this type pretty much completes
   * the type system :)
   * Note that classes' and instances' member list can change during execution, so it is important to construct an instance of PyClassType
   * right in the place of reference, so that such changes could possibly be accounted for.
   *
   * @param source       PyClass which defines this type. For builtin or external classes, skeleton files contain the definitions.
   * @param isDefinition whether this type describes an instance or a definition of the class.
   */
  public PyClassTypeImpl(@NotNull PyClass source, boolean isDefinition) {
    PyClass originalElement = CompletionUtil.getOriginalElement(source);
    myClass = originalElement != null ? originalElement : source;
    myIsDefinition = isDefinition;
  }

  public <T> PyClassTypeImpl withUserData(Key<T> key, T value) {
    putUserData(key, value);
    return this;
  }

  /**
   * @return a PyClass which defined this type.
   */
  @Override
  @NotNull
  public PyClass getPyClass() {
    return myClass;
  }

  /**
   * @return whether this type refers to an instance or a definition of the class.
   */
  @Override
  public boolean isDefinition() {
    return myIsDefinition;
  }

  @Override
  public PyClassType toInstance() {
    return myIsDefinition ? withUserDataCopy(new PyClassTypeImpl(myClass, false)) : this;
  }

  /**
   * Wrap new instance to copy user data to it
   */
  @NotNull
  final <T extends PyClassTypeImpl> T withUserDataCopy(@NotNull final T newInstance) {
    newInstance.setUserMap(getUserMap());
    return newInstance;
  }

  @Override
  @Nullable
  public String getClassQName() {
    return myClass.getQualifiedName();
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    return myClass.getSuperClassTypes(context);
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext,
                                                          boolean inherited) {
    final Set<Pair<PyClass, String>> resolving = ourResolveMemberStack.get();
    final Pair<PyClass, String> key = Pair.create(myClass, name);
    if (resolving.contains(key)) {
      return Collections.emptyList();
    }
    resolving.add(key);
    try {
      return doResolveMember(name, location, direction, resolveContext, inherited);
    }
    finally {
      resolving.remove(key);
    }
  }

  @Nullable
  private List<? extends RatedResolveResult> doResolveMember(@NotNull String name,
                                                             @Nullable PyExpression location,
                                                             @NotNull AccessDirection direction,
                                                             @NotNull PyResolveContext resolveContext,
                                                             boolean inherited) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    PsiElement classMember =
      resolveByOverridingMembersProviders(this, name, location, context); //overriding members provers have priority to normal resolve
    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    if (resolveContext.allowProperties()) {
      final Ref<ResolveResultList> resultRef = findProperty(name, direction, true, resolveContext.getTypeEvalContext());
      if (resultRef != null) {
        return resultRef.get();
      }
    }

    if ("super".equals(getClassQName()) && isBuiltin() && location instanceof PyCallExpression) {
      // methods of super() call are not of class super!
      PyExpression first_arg = ((PyCallExpression)location).getArgument(0, PyExpression.class);
      if (first_arg != null) { // the usual case: first arg is the derived class that super() is proxying for
        PyType first_arg_type = context.getType(first_arg);
        if (first_arg_type instanceof PyClassType) {
          PyClass derived_class = ((PyClassType)first_arg_type).getPyClass();
          final Iterator<PyClass> base_it = derived_class.getAncestorClasses(context).iterator();
          if (base_it.hasNext()) {
            return new PyClassTypeImpl(base_it.next(), true).resolveMember(name, location, direction, resolveContext);
          }
          else {
            return null; // no base classes = super() cannot proxy anything meaningful from a base class
          }
        }
      }
    }

    classMember = resolveClassMember(myClass, myIsDefinition, name, location);

    if (PyNames.__CLASS__.equals(name)) {
      return resolveDunderClass(context, classMember);
    }

    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    if (PyNames.DOC.equals(name)) {
      return Optional
        .ofNullable(PyBuiltinCache.getInstance(myClass).getObjectType())
        .map(type -> type.resolveMember(name, location, direction, resolveContext))
        .orElse(Collections.emptyList());
    }

    classMember = resolveByOverridingAncestorsMembersProviders(this, name, location);
    if (classMember != null) {
      final ResolveResultList list = new ResolveResultList();
      int rate = RatedResolveResult.RATE_NORMAL;
      for (PyResolveResultRater rater : Extensions.getExtensions(PyResolveResultRater.EP_NAME)) {
        rate += rater.getMemberRate(classMember, this, context);
      }
      list.poke(classMember, rate);
      return list;
    }


    if (inherited) {
      for (PyClassLikeType type : myClass.getAncestorTypes(context)) {
        if (type instanceof PyClassType) {
          if (!myIsDefinition) {
            type = type.toInstance();
          }
          PsiElement superMember = resolveClassMember(((PyClassType)type).getPyClass(), myIsDefinition, name, null);
          if (superMember != null) {
            return ResolveResultList.to(superMember);
          }
        }
        if (type != null) {
          final List<? extends RatedResolveResult> results = type.resolveMember(name, location, direction, resolveContext, false);
          if (results != null && !results.isEmpty()) {
            return results;
          }
        }
      }
    }

    if (inherited && isDefinition() && myClass.isNewStyleClass(context)) {
      final PyClassLikeType typeType = getMetaClassType(context, true);
      if (typeType != null) {
        List<? extends RatedResolveResult> typeMembers = typeType.resolveMember(name, location, direction, resolveContext);
        if (typeMembers != null && !typeMembers.isEmpty()) {
          return typeMembers;
        }
      }
    }

    if (inherited) {
      classMember =
        resolveByMembersProviders(this, name, location,
                                  context);  //ask providers after real class introspection as providers have less priority
    }

    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    if (inherited) {
      for (PyClassLikeType type : myClass.getAncestorTypes(context)) {
        if (type instanceof PyClassType) {
          final PyClass pyClass = ((PyClassType)type).getPyClass();
          PsiElement superMember =
            resolveByMembersProviders(new PyClassTypeImpl(pyClass, isDefinition()), name, location, resolveContext.getTypeEvalContext());

          if (superMember != null) {
            return ResolveResultList.to(superMember);
          }
        }
      }
    }

    return Collections.emptyList();
  }

  private Ref<ResolveResultList> findProperty(String name,
                                              AccessDirection direction,
                                              boolean inherited,
                                              @Nullable TypeEvalContext context) {
    Ref<ResolveResultList> resultRef = null;
    Property property = myClass.findProperty(name, inherited, context);
    if (property != null) {
      Maybe<PyCallable> accessor = property.getByDirection(direction);
      if (accessor.isDefined()) {
        PyCallable accessor_code = accessor.value();
        ResolveResultList ret = new ResolveResultList();
        if (accessor_code != null) ret.poke(accessor_code, RatedResolveResult.RATE_NORMAL);
        PyTargetExpression site = property.getDefinitionSite();
        if (site != null) ret.poke(site, RatedResolveResult.RATE_LOW);
        if (ret.size() > 0) {
          resultRef = Ref.create(ret);
        }
        else {
          resultRef = Ref.create();
        } // property is found, but the required accessor is explicitly absent
      }
    }
    return resultRef;
  }

  @Nullable
  private List<? extends RatedResolveResult> resolveDunderClass(@NotNull TypeEvalContext context, @Nullable PsiElement classMember) {
    final boolean newStyleClass = myClass.isNewStyleClass(context);

    if (!myIsDefinition) {
      if (newStyleClass && classMember != null) {
        return ResolveResultList.to(classMember);
      }

      return ResolveResultList.to(
        myClass.getAncestorClasses(context)
        .stream()
        .filter(cls -> !PyUtil.isObjectClass(cls))
        .<PsiElement>map(cls -> cls.findClassAttribute(PyNames.__CLASS__, true, context))
        .filter(target -> target != null)
        .findFirst()
        .orElse(myClass)
      );
    }

    if (LanguageLevel.forElement(myClass).isOlderThan(LanguageLevel.PYTHON30) && !newStyleClass) {
      return ResolveResultList.to(classMember);
    }

    return Optional
      .ofNullable(PyBuiltinCache.getInstance(myClass).getTypeType())
      .map(typeType -> ResolveResultList.to(typeType.getPyClass()))
      .orElse(null);
  }

  @Nullable
  @Override
  public PyClassLikeType getMetaClassType(@NotNull final TypeEvalContext context, boolean inherited) {
    if (!inherited) {
      return PyUtil.as(myClass.getMetaClassType(context), PyClassLikeType.class);
    }
    final List<PyClassLikeType> metaClassTypes = getAllExplicitMetaClassTypes(context);
    final PyClassLikeType mostDerivedMeta = getMostDerivedClassType(metaClassTypes, context);
    return mostDerivedMeta != null ? mostDerivedMeta : PyBuiltinCache.getInstance(myClass).getObjectType("type");
  }

  @Nullable
  private static PyClassLikeType getMostDerivedClassType(@NotNull List<PyClassLikeType> classTypes,
                                                         @NotNull final TypeEvalContext context) {
    if (classTypes.isEmpty()) {
      return null;
    }
    try {
      return Collections.max(classTypes, (t1, t2) -> {
        if (t1 == t2 || t1 != null && t1.equals(t2)) {
          return 0;
        }
        else if (t2 == null || t1 != null && Sets.newHashSet(t1.getAncestorTypes(context)).contains(t2)) {
          return 1;
        }
        else if (t1 == null || Sets.newHashSet(t2.getAncestorTypes(context)).contains(t1)) {
          return -1;
        }
        else {
          throw new NotDerivedClassTypeException();
        }
      });
    }
    catch (NotDerivedClassTypeException ignored) {
      return null;
    }
  }

  private static final class NotDerivedClassTypeException extends RuntimeException {
  }

  private List<PyClassLikeType> getAllExplicitMetaClassTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> results = Lists.newArrayList();
    final PyClassLikeType ownMeta = getMetaClassType(context, false);
    if (ownMeta != null) {
      results.add(ownMeta);
    }
    for (PyClassLikeType ancestor : myClass.getAncestorTypes(context)) {
      if (ancestor != null) {
        final PyClassLikeType ancestorMeta = ancestor.getMetaClassType(context, false);
        if (ancestorMeta != null) {
          results.add(ancestorMeta);
        }
      }
    }
    return results;
  }

  @Override
  public boolean isCallable() {
    if (isDefinition()) {
      return true;
    }
    if (isMethodType(this)) {
      return true;
    }
    final PyClass cls = getPyClass();
    if (PyABCUtil.isSubclass(cls, PyNames.CALLABLE, null)) {
      return true;
    }
    return false;
  }

  private static boolean isMethodType(@NotNull PyClassType type) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(type.getPyClass());
    return type.equals(builtinCache.getClassMethodType()) || type.equals(builtinCache.getStaticMethodType());
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return getPossibleCallType(context, null);
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return getPossibleCallType(context, callSite);
  }

  @Nullable
  private PyType getPossibleCallType(@NotNull TypeEvalContext context, @Nullable PyCallSiteExpression callSite) {
    if (!isDefinition()) {
      return PyUtil.getReturnTypeOfMember(this, PyNames.CALL, callSite, context);
    }
    else {
      return withUserDataCopy(new PyClassTypeImpl(getPyClass(), false));
    }
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return null;
  }

  @NotNull
  @Override
  public final List<PyClassLikeType> getAncestorTypes(@NotNull final TypeEvalContext context) {
    return myClass.getAncestorTypes(context);
  }

  @Nullable
  private static PsiElement resolveClassMember(@NotNull PyClass cls,
                                               boolean isDefinition,
                                               @NotNull String name,
                                               @Nullable PyExpression location) {
    PsiElement result = resolveInner(cls, isDefinition, name, location);
    if (result != null) {
      return result;
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveByMembersProviders(PyClassType aClass,
                                                      String name,
                                                      @Nullable PsiElement location,
                                                      TypeEvalContext context) {
    for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      final PsiElement resolveResult = provider.resolveMember(aClass, name, location, context);
      if (resolveResult != null) return resolveResult;
    }

    return null;
  }

  @Nullable
  private static PsiElement resolveByOverridingMembersProviders(PyClassType aClass,
                                                                String name,
                                                                @Nullable PsiElement location,
                                                                @NotNull final TypeEvalContext context) {
    for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      if (provider instanceof PyOverridingClassMembersProvider) {
        final PsiElement resolveResult = provider.resolveMember(aClass, name, location, context);
        if (resolveResult != null) return resolveResult;
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement resolveByOverridingAncestorsMembersProviders(PyClassType type, String name, @Nullable PyExpression location) {
    for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      if (provider instanceof PyOverridingAncestorsClassMembersProvider) {
        final PsiElement resolveResult = provider.resolveMember(type, name, location, null);
        if (resolveResult != null) return resolveResult;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveInner(@NotNull PyClass cls,
                                         boolean isDefinition,
                                         @NotNull String name,
                                         @Nullable PyExpression location) {
    final PyResolveProcessor processor = new PyResolveProcessor(name);
    if (!isDefinition) {
      if (!cls.processInstanceLevelDeclarations(processor, location)) {
        return Iterables.getFirst(processor.getElements(), null);
      }
    }
    cls.processClassLevelDeclarations(processor);
    return Iterables.getFirst(processor.getElements(), null);
  }

  private static Key<Set<PyClassType>> CTX_VISITED = Key.create("PyClassType.Visited");
  public static Key<Boolean> CTX_SUPPRESS_PARENTHESES = Key.create("PyFunction.SuppressParentheses");

  public Object[] getCompletionVariants(String prefix, PsiElement location, ProcessingContext context) {
    Set<PyClassType> visited = context.get(CTX_VISITED);
    if (visited == null) {
      visited = new HashSet<>();
      context.put(CTX_VISITED, visited);
    }
    if (visited.contains(this)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    visited.add(this);
    Set<String> namesAlready = context.get(CTX_NAMES);
    if (namesAlready == null) {
      namesAlready = new HashSet<>();
    }
    List<Object> ret = new ArrayList<>();

    boolean suppressParentheses = context.get(CTX_SUPPRESS_PARENTHESES) != null;
    addOwnClassMembers(location, namesAlready, suppressParentheses, ret, prefix);

    PsiFile origin = (location != null) ?
                     CompletionUtil.getOriginalOrSelf(location)
                       .getContainingFile() :
                     null;
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(myClass.getProject(), origin);
    addInheritedMembers(prefix, location, namesAlready, context, ret, typeEvalContext);

    // from providers
    for (final PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      for (final PyCustomMember member : provider.getMembers(this, location, typeEvalContext)) {
        final String name = member.getName();
        if (!namesAlready.contains(name)) {
          ret.add(PyCustomMemberUtils.toLookUpElement(member, getName()));
        }
      }
    }

    if (!myClass.isNewStyleClass(typeEvalContext)) {
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(myClass);
      final PyClassType classobjType = cache.getOldstyleClassobjType();
      if (classobjType != null) {
        ret.addAll(Arrays.asList(classobjType.getCompletionVariants(prefix, location, context)));
      }
    }

    if (isDefinition() && myClass.isNewStyleClass(typeEvalContext)) {
      final PyClassLikeType typeType = getMetaClassType(typeEvalContext, true);
      if (typeType != null) {
        Collections.addAll(ret, typeType.getCompletionVariants(prefix, location, context));
      }
    }

    return ret.toArray();
  }

  @Override
  public void visitMembers(@NotNull final Processor<PsiElement> processor,
                           final boolean inherited,
                           @NotNull final TypeEvalContext context) {
    myClass.visitMethods(new MyProcessorWrapper<>(processor), false, context);
    myClass.visitClassAttributes(new MyProcessorWrapper<>(processor), false, context);

    for (PyTargetExpression expression : myClass.getInstanceAttributes()) {
      processor.process(expression);
    }

    if (!inherited) {
      return;
    }

    for (final PyClassLikeType type : getAncestorTypes(context)) {
      if (type != null) {
        // "false" because getAncestorTypes returns ALL ancestors, not only direct parents
        type.visitMembers(processor, false, context);
      }
    }
  }

  @NotNull
  @Override
  public Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    final Set<String> result = new LinkedHashSet<>();

    for (PyFunction function : myClass.getMethods()) {
      result.add(function.getName());
    }

    for (PyTargetExpression expression : myClass.getClassAttributes()) {
      result.add(expression.getName());
    }

    for (PyTargetExpression expression : myClass.getInstanceAttributes()) {
      result.add(expression.getName());
    }

    for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
      for (PyCustomMember member : provider.getMembers(this, null, context)) {
        result.add(member.getName());
      }
    }

    if (inherited) {
      for (PyClassLikeType type : getAncestorTypes(context)) {
        if (type != null) {
          final PyClassLikeType ancestorType = isDefinition() ? type : type.toInstance();

          result.addAll(ancestorType.getMemberNames(false, context));
        }
      }
    }

    return result;
  }

  private void addOwnClassMembers(PsiElement expressionHook,
                                  Set<String> namesAlready,
                                  boolean suppressParentheses,
                                  List<Object> ret,
                                  @Nullable final String prefix) {
    PyClass containingClass = PsiTreeUtil.getParentOfType(expressionHook, PyClass.class);
    if (containingClass != null) {
      containingClass = CompletionUtil.getOriginalElement(containingClass);
    }
    boolean withinOurClass = containingClass == getPyClass() || isInSuperCall(expressionHook);

    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(
      expressionHook, new FilterNotInstance(myClass), null
    );
    if (suppressParentheses) {
      processor.suppressParentheses();
    }
    myClass.processClassLevelDeclarations(processor);

    // We are here because of completion (see call stack), so we use code complete here
    final TypeEvalContext context =
      (expressionHook != null ? TypeEvalContext.codeCompletion(myClass.getProject(), myClass.getContainingFile()) : null);
    List<String> slots = myClass.isNewStyleClass(context) ? myClass.getSlots(
      context) : null;
    if (slots != null) {
      processor.setAllowedNames(slots);
    }
    myClass.processInstanceLevelDeclarations(processor, expressionHook);

    for (LookupElement le : processor.getResultList()) {
      String name = le.getLookupString();
      if (namesAlready.contains(name)) continue;
      if (!withinOurClass && isClassPrivate(name)) continue;
      if (!withinOurClass && isClassProtected(name) && prefix == null) continue;
      namesAlready.add(name);
      ret.add(le);
    }
    if (slots != null) {
      for (String name : slots) {
        if (!namesAlready.contains(name)) {
          ret.add(LookupElementBuilder.create(name));
        }
      }
    }
  }

  private static boolean isInSuperCall(PsiElement hook) {
    if (hook instanceof PyReferenceExpression) {
      final PyExpression qualifier = ((PyReferenceExpression)hook).getQualifier();
      return qualifier instanceof PyCallExpression && ((PyCallExpression)qualifier).isCalleeText(PyNames.SUPER);
    }
    return false;
  }

  private void addInheritedMembers(String name,
                                   PsiElement expressionHook,
                                   Set<String> namesAlready,
                                   ProcessingContext context,
                                   List<Object> ret,
                                   @NotNull TypeEvalContext typeEvalContext) {
    for (PyType type : myClass.getSuperClassTypes(typeEvalContext)) {
      if (!(type instanceof PyClassLikeType)) {
        continue;
      }

      final PyClassLikeType classLikeType = (PyClassLikeType)type;
      if (classLikeType.isDefinition() && !myIsDefinition) {
        type = classLikeType.toInstance();
      }

      Object[] ancestry = type.getCompletionVariants(name, expressionHook, context);
      for (Object ob : ancestry) {
        String inheritedName = ob.toString();
        if (!namesAlready.contains(inheritedName) && !isClassPrivate(inheritedName)) {
          ret.add(ob);
          namesAlready.add(inheritedName);
        }
      }
      ContainerUtil.addAll(ret, ancestry);
    }
  }

  private static boolean isClassPrivate(String lookup_string) {
    return lookup_string.startsWith("__") && !lookup_string.endsWith("__");
  }

  private static boolean isClassProtected(@NotNull final String lookupString) {
    return lookupString.startsWith("_") && !lookupString.startsWith("__");
  }

  public String getName() {
    return getPyClass().getName();
  }

  @Override
  public boolean isBuiltin() {
    return PyBuiltinCache.getInstance(myClass).isBuiltin(myClass);
  }

  @Override
  public void assertValid(String message) {
    if (!myClass.isValid()) {
      throw new PsiInvalidElementAccessException(myClass, myClass.getClass().toString() + ": " + message);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyClassTypeImpl classType = (PyClassTypeImpl)o;

    if (myIsDefinition != classType.myIsDefinition) return false;
    if (!myClass.equals(classType.myClass)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myClass.hashCode();
    result = 31 * result + (myIsDefinition ? 1 : 0);
    return result;
  }

  public static boolean is(@NotNull String qName, PyType type) {
    if (type instanceof PyClassType) {
      return qName.equals(((PyClassType)type).getClassQName());
    }
    return false;
  }

  @Override
  public String toString() {
    return (isValid() ? "" : "[INVALID] ") + "PyClassType: " + getClassQName();
  }

  public boolean isValid() {
    return myClass.isValid();
  }

  @Nullable
  public static PyClassTypeImpl createTypeByQName(@NotNull final PsiElement anchor,
                                                  @NotNull final String classQualifiedName,
                                                  final boolean isDefinition) {
    final PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
    if (pyClass == null) {
      return null;
    }
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  private static final class MyProcessorWrapper<T extends PsiElement> implements Processor<T> {
    private final Processor<PsiElement> myProcessor;

    private MyProcessorWrapper(@NotNull final Processor<PsiElement> processor) {
      myProcessor = processor;
    }

    @Override
    public boolean process(final T t) {
      myProcessor.process(t);
      return true;
    }
  }

  /**
   * Accepts only targets that are not the given object.
   */
  public static class FilterNotInstance implements Condition<PsiElement> {
    Object instance;

    public FilterNotInstance(Object instance) {
      this.instance = instance;
    }

    public boolean value(final PsiElement target) {
      return (instance != target);
    }
  }
}
