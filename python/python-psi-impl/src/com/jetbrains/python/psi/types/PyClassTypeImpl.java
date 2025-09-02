// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PyCustomMemberUtils;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyResolveResultRater;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;


public class PyClassTypeImpl extends UserDataHolderBase implements PyClassType {

  protected final @NotNull PyClass myClass;
  protected final boolean myIsDefinition;

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
    PyClass originalElement = CompletionUtilCoreImpl.getOriginalElement(source);
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
  public @NotNull PyClass getPyClass() {
    return myClass;
  }


  @Override
  public @NotNull PyQualifiedNameOwner getDeclarationElement() {
    return getPyClass();
  }

  /**
   * @return whether this type refers to an instance or a definition of the class.
   */
  @Override
  public boolean isDefinition() {
    return myIsDefinition;
  }

  @Override
  public @NotNull PyClassType toInstance() {
    return myIsDefinition ? withUserDataCopy(new PyClassTypeImpl(myClass, false)) : this;
  }

  @Override
  public @NotNull PyClassLikeType toClass() {
    return myIsDefinition ? this : new PyClassTypeImpl(myClass, true);
  }

  /**
   * Wrap new instance to copy user data to it
   */
  final @NotNull <T extends PyClassTypeImpl> T withUserDataCopy(final @NotNull T newInstance) {
    newInstance.setUserMap(getUserMap());
    return newInstance;
  }

  @Override
  public @Nullable String getClassQName() {
    return myClass.getQualifiedName();
  }

  @Override
  public @NotNull List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    return myClass.getSuperClassTypes(context);
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(final @NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(final @NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext,
                                                                    boolean inherited) {
    return RecursionManager.doPreventingRecursion(
      resolveContext.allowProperties()
      ? Arrays.asList(this, name, location, direction, resolveContext)
      : Arrays.asList(this, name, location, resolveContext),
      false,
      () -> doResolveMember(name, location, direction, resolveContext, inherited)
    );
  }

  private @Nullable List<? extends RatedResolveResult> doResolveMember(@NotNull String name,
                                                                       @Nullable PyExpression location,
                                                                       @NotNull AccessDirection direction,
                                                                       @NotNull PyResolveContext resolveContext,
                                                                       boolean inherited) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    PsiElement classMember =
      resolveByOverridingMembersProviders(this, name, location, resolveContext); //overriding members provers have priority to normal resolve
    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    if (resolveContext.allowProperties()) {
      final Ref<ResolveResultList> resultRef = findProperty(name, direction, false, resolveContext.getTypeEvalContext());
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

    final List<? extends RatedResolveResult> classMembers = resolveInner(myClass, myIsDefinition, name, location, context);
    if (!classMembers.isEmpty()) {
      return classMembers;
    }

    classMember = resolveByOverridingAncestorsMembersProviders(this, name, location, resolveContext);
    if (classMember != null) {
      final ResolveResultList list = new ResolveResultList();
      int rate = RatedResolveResult.RATE_NORMAL;
      for (PyResolveResultRater rater : PyResolveResultRater.EP_NAME.getExtensionList()) {
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
          final List<? extends RatedResolveResult> superMembers =
            resolveInner(((PyClassType)type).getPyClass(), myIsDefinition, name, location, context);
          if (!superMembers.isEmpty()) {
            return superMembers;
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

    if (inherited && !PyNames.INIT.equals(name) && !PyNames.NEW.equals(name)) {
      final List<? extends RatedResolveResult> typeMembers = resolveMetaClassMember(name, location, direction, resolveContext);
      if (typeMembers != null) {
        return typeMembers;
      }
    }

    if (inherited) {
      classMember =
        resolveByMembersProviders(this, name, location,
                                  resolveContext);  //ask providers after real class introspection as providers have less priority
    }

    if (classMember != null) {
      return ResolveResultList.to(classMember);
    }

    if (inherited) {
      for (PyClassLikeType type : myClass.getAncestorTypes(context)) {
        if (type instanceof PyClassType) {
          final PyClass pyClass = ((PyClassType)type).getPyClass();
          PsiElement superMember =
            resolveByMembersProviders(new PyClassTypeImpl(pyClass, isDefinition()), name, location, resolveContext);

          if (superMember != null) {
            return ResolveResultList.to(superMember);
          }
        }
      }
    }

    return Collections.emptyList();
  }

  private @Nullable List<? extends RatedResolveResult> resolveMetaClassMember(@NotNull String name,
                                                                              @Nullable PyExpression location,
                                                                              @NotNull AccessDirection direction,
                                                                              @NotNull PyResolveContext resolveContext) {
    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    if (!myClass.isNewStyleClass(context)) {
      return null;
    }

    final PyClassLikeType typeType = getMetaClassType(context, true);
    if (typeType == null) {
      return null;
    }

    if (isDefinition()) {
      final List<? extends RatedResolveResult> typeMembers = typeType.resolveMember(name, location, direction, resolveContext);
      if (!ContainerUtil.isEmpty(typeMembers)) {
        return typeMembers;
      }

      final List<? extends RatedResolveResult> typeInstanceMembers =
        typeType.toInstance().resolveMember(name, location, direction, resolveContext);

      if (!ContainerUtil.isEmpty(typeInstanceMembers)) {
        return typeInstanceMembers;
      }
    }
    else if (typeType instanceof PyClassType) {
      final List<PyTargetExpression> typeInstanceAttributes = ((PyClassType)typeType).getPyClass().getInstanceAttributes();

      if (!ContainerUtil.isEmpty(typeInstanceAttributes)) {
        final List<RatedResolveResult> typeInstanceAttributesWithSpecifiedName = typeInstanceAttributes
          .stream()
          .filter(member -> name.equals(member.getName()))
          .map(member -> new RatedResolveResult(PyReferenceImpl.getRate(member, context), member))
          .collect(Collectors.toList());

        if (!typeInstanceAttributesWithSpecifiedName.isEmpty()) {
          return typeInstanceAttributesWithSpecifiedName;
        }
      }
    }

    return null;
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
        if (!ret.isEmpty()) {
          resultRef = Ref.create(ret);
        }
        else {
          resultRef = Ref.create();
        } // property is found, but the required accessor is explicitly absent
      }
    }
    return resultRef;
  }

  @Override
  public @Nullable PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited) {
    return myClass.getMetaClassType(inherited, context);
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

  @Override
  public @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    final var resolveContext = PyResolveContext.defaultContext(context);

    return StreamEx
      .of(PyUtil.filterTopPriorityElements(PyCallExpressionHelper.resolveImplicitlyInvokedMethods(this, null, resolveContext)))
      .select(PyCallable.class)
      .map(callable -> callable.getParameters(context))
      .findFirst()
      // If resolved parameters are empty, consider them as invalid and return null
      .filter(parameters -> !parameters.isEmpty())
      // Skip "self" for __init__/__call__ and "cls" for __new__
      .map(parameters -> ContainerUtil.subList(parameters, 1))
      .orElse(null);
  }

  private static boolean isMethodType(@NotNull PyClassType type) {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(type.getPyClass());
    return type.equals(builtinCache.getClassMethodType()) ||
           type.equals(builtinCache.getStaticMethodType()) ||
           type.equals(builtinCache.getObjectType(PyNames.FUNCTION));
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context) {
    return getPossibleCallType(context, null);
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return getPossibleCallType(context, callSite);
  }

  private @Nullable PyType getPossibleCallType(@NotNull TypeEvalContext context, @Nullable PyCallSiteExpression callSite) {
    if (!isDefinition()) {
      return PyUtil.getReturnTypeOfMember(this, PyNames.CALL, callSite, context);
    }
    else {
      return withUserDataCopy(new PyClassTypeImpl(getPyClass(), false));
    }
  }

  @Override
  public @NotNull List<PyClassLikeType> getAncestorTypes(final @NotNull TypeEvalContext context) {
    return myClass.getAncestorTypes(context);
  }

  private static @Nullable PsiElement resolveByMembersProviders(PyClassType aClass,
                                                                String name,
                                                                @Nullable PsiElement location,
                                                                @NotNull PyResolveContext resolveContext) {
    for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
      final PsiElement resolveResult = provider.resolveMember(aClass, name, location, resolveContext);
      if (resolveResult != null) return resolveResult;
    }

    return null;
  }

  private static @Nullable PsiElement resolveByOverridingMembersProviders(PyClassType aClass,
                                                                          String name,
                                                                          @Nullable PsiElement location,
                                                                          @NotNull PyResolveContext resolveContext) {
    for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof PyOverridingClassMembersProvider) {
        final PsiElement resolveResult = provider.resolveMember(aClass, name, location, resolveContext);
        if (resolveResult != null) return resolveResult;
      }
    }

    return null;
  }

  private static @Nullable PsiElement resolveByOverridingAncestorsMembersProviders(PyClassType type,
                                                                                   String name,
                                                                                   @Nullable PyExpression location,
                                                                                   @NotNull PyResolveContext resolveContext) {
    for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof PyOverridingAncestorsClassMembersProvider) {
        final PsiElement resolveResult = provider.resolveMember(type, name, location, resolveContext);
        if (resolveResult != null) return resolveResult;
      }
    }
    return null;
  }

  private static @NotNull List<? extends RatedResolveResult> resolveInner(@NotNull PyClass cls,
                                                                          boolean isDefinition,
                                                                          @NotNull String name,
                                                                          @Nullable PyExpression location,
                                                                          @NotNull TypeEvalContext context) {
    final PyAttributesProcessor processor = new PyAttributesProcessor(name, location);
    final Map<PsiElement, PyImportedNameDefiner> results;

    if (isDefinition || cls.processInstanceLevelDeclarations(processor, location)) {
      cls.processClassObjectAttributes(processor, location);
    }
    results = processor.getResults();

    return EntryStream
      .of(results)
      .mapKeyValue(
        (element, definer) -> {
          final int rate = PyReferenceImpl.getRate(element, context);
          return definer != null ? new ImportedResolveResult(element, rate, definer) : new RatedResolveResult(rate, element);
        }
      )
      .toList();
  }

  private static final Key<Set<PyClassType>> CTX_VISITED = Key.create("PyClassType.Visited");
  public static final Key<Boolean> CTX_SUPPRESS_PARENTHESES = Key.create("PyFunction.SuppressParentheses");

  @Override
  public Object @NotNull [] getCompletionVariants(String prefix, PsiElement location, @NotNull ProcessingContext context) {
    if (isRecursive(context)) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    final Set<String> visited = visitedNames(context);

    final PsiFile origin = location != null ? CompletionUtilCoreImpl.getOriginalOrSelf(location).getContainingFile() : null;
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(myClass.getProject(), origin);

    final boolean withinOurClass = withinClass(getPyClass(), location);
    final Condition<String> nameFilter = name -> {
      if (!withinOurClass && PyUtil.isClassPrivateName(name)) return false;
      if (!withinOurClass && isClassProtected(name) && prefix == null) return false;
      return visited.add(name);
    };

    final CompletionVariantsProcessor processor =
      new CompletionVariantsProcessor(location, null, nameFilter, false, context.get(CTX_SUPPRESS_PARENTHESES) != null);

    processMembers(processor, () -> processor.setAllowedNames(myClass.getSlots(typeEvalContext)));
    final List<Object> result = new ArrayList<>(processor.getResultList());

    processOwnSlots(
      slot -> {
        if (visited.add(slot)) result.add(LookupElementBuilder.create(slot));
        return true;
      },
      typeEvalContext
    );

    // provided
    processProvidedMembers(
      member -> {
        if (visited.add(member.getName())) result.add(PyCustomMemberUtils.toLookUpElement(member, getName()));
        return true;
      },
      location,
      typeEvalContext
    );

    // inherited
    prepareTypesForProcessingMembers(getSuperClassTypes(typeEvalContext))
      .flatArray(type -> type.getCompletionVariants(prefix, location, context))
      .into(result);

    processMetaClassMembers(
      typeType -> ContainerUtil.addAll(result, typeType.getCompletionVariants(prefix, location, context)),
      typeInstanceAttribute -> {
        result.add(LookupElementBuilder.create(typeInstanceAttribute));
        return true;
      },
      typeEvalContext
    );

    return result.toArray();
  }

  private boolean isRecursive(@NotNull ProcessingContext context) {
    Set<PyClassType> types = context.get(CTX_VISITED);
    if (types == null) {
      types = new HashSet<>();
      context.put(CTX_VISITED, types);
    }
    return !types.add(this);
  }

  private static @NotNull Set<String> visitedNames(@NotNull ProcessingContext context) {
    Set<String> names = context.get(CTX_NAMES);
    if (names == null) {
      names = new HashSet<>();
      context.put(CTX_NAMES, names);
    }
    return names;
  }

  private static boolean withinClass(@NotNull PyClass cls, @Nullable PsiElement location) {
    PyClass containingClass = PsiTreeUtil.getParentOfType(location, PyClass.class);
    if (containingClass != null) {
      containingClass = CompletionUtilCoreImpl.getOriginalElement(containingClass);
    }
    return containingClass == PyiUtil.getOriginalElementOrLeaveAsIs(cls, PyClass.class) || isInSuperCall(location);
  }

  @Override
  public void visitMembers(@NotNull Processor<? super PsiElement> processor, boolean inherited, @NotNull TypeEvalContext context) {
    processMembers(processor);

    if (inherited) {
      prepareTypesForProcessingMembers(getAncestorTypes(context)).forEach(type -> type.visitMembers(processor, false, context));
      processMetaClassMembers(typeType -> typeType.visitMembers(processor, true, context), processor, context);
    }
  }

  @Override
  public @NotNull Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    // PyNamedTupleType.getMemberNames provide names that we are not able to visit,
    // so this method could not be replaced with PyClassLikeType.visitMembers

    final Set<String> result = new LinkedHashSet<>();

    processMembers(
      element -> {
        if (element instanceof PsiNamedElement) ContainerUtil.addIfNotNull(result, ((PsiNamedElement)element).getName());
        return true;
      }
    );

    processOwnSlots(
      slot -> {
        result.add(slot);
        return true;
      },
      context
    );

    processProvidedMembers(
      member -> {
        result.add(member.getName());
        return true;
      },
      null,
      context
    );

    if (inherited) {
      prepareTypesForProcessingMembers(getAncestorTypes(context))
        .flatCollection(type -> type.getMemberNames(false, context))
        .into(result);

      processMetaClassMembers(
        typeType -> result.addAll(typeType.getMemberNames(true, context)),
        instanceTypeAttribute -> {
          ContainerUtil.addIfNotNull(result, instanceTypeAttribute.getName());
          return true;
        },
        context
      );
    }

    return result;
  }

  private void processMembers(@NotNull Processor<? super PsiElement> processor) {
    final PsiScopeProcessor scopeProcessor = new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        return processor.process(element);
      }
    };

    processMembers(scopeProcessor, EmptyRunnable.getInstance());
  }

  private void processMembers(@NotNull PsiScopeProcessor scopeProcessor, @NotNull Runnable afterClassLevelBeforeInstanceLevel) {
    myClass.processClassObjectAttributes(scopeProcessor, null);
    if (!isDefinition()) {
      afterClassLevelBeforeInstanceLevel.run();
      myClass.processInstanceLevelDeclarations(scopeProcessor, null);
    }
  }

  private void processOwnSlots(@NotNull Processor<? super String> processor, @NotNull TypeEvalContext context) {
    if (myClass.isNewStyleClass(context)) {
      for (String slot : ContainerUtil.notNullize(myClass.getOwnSlots())) {
        if (!processor.process(slot)) return;
      }
    }
  }

  private void processProvidedMembers(@NotNull Processor<? super PyCustomMember> processor,
                                      @Nullable PsiElement location,
                                      @NotNull TypeEvalContext context) {
    for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
      for (PyCustomMember member : provider.getMembers(this, location, context)) {
        if (!processor.process(member)) return;
      }
    }
  }

  private @NotNull StreamEx<PyClassLikeType> prepareTypesForProcessingMembers(@NotNull List<PyClassLikeType> types) {
    return StreamEx.of(types).nonNull().map(type -> isDefinition() ? type.toClass() : type.toInstance());
  }

  private void processMetaClassMembers(@NotNull Consumer<? super PyClassLikeType> typeTypeConsumer,
                                       @NotNull Processor<? super PyTargetExpression> instanceTypeAttributesProcessor,
                                       @NotNull TypeEvalContext context) {
    if (!myClass.isNewStyleClass(context)) return;

    final PyClassLikeType typeType = getMetaClassType(context, true);
    if (typeType == null) return;

    if (isDefinition()) {
      typeTypeConsumer.accept(typeType.toInstance());
    }
    else if (typeType instanceof PyClassType) {
      for (PyTargetExpression attribute : ((PyClassType)typeType).getPyClass().getInstanceAttributes()) {
        if (!instanceTypeAttributesProcessor.process(attribute)) return;
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

  private static boolean isClassProtected(final @NotNull String lookupString) {
    return lookupString.startsWith("_") && !lookupString.startsWith("__");
  }

  @Override
  public @Nullable String getName() {
    if (isNoneType(this)) return PyNames.NONE;
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

  @Override
  public String toString() {
    return (isValid() ? "" : "[INVALID] ") + "PyClassType: " + getClassQName();
  }

  @Override
  public boolean isValid() {
    return myClass.isValid();
  }

  @Override
  public boolean isAttributeWritable(@NotNull String name, @NotNull TypeEvalContext context) {
    final PyClass cls = getPyClass();

    if (isDefinition() || PyUtil.isObjectClass(cls)) return true;

    final List<String> slots = cls.getSlots(context);
    final Condition<PyTargetExpression> isDefinedTarget = target -> name.equals(target.getName()) && target.hasAssignedValue();

    return slots == null ||
           slots.contains(name) && !ContainerUtil.exists(cls.getClassAttributesInherited(context), isDefinedTarget) ||
           cls.findProperty(name, true, context) != null;
  }

  public static @Nullable PyClassTypeImpl createTypeByQName(final @NotNull PsiElement anchor,
                                                            final @NotNull String classQualifiedName,
                                                            final boolean isDefinition) {
    final PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
    if (pyClass == null) {
      return null;
    }
    return new PyClassTypeImpl(pyClass, isDefinition);
  }

  /**
   * <p>Control flow aware Python attributes resolver.</p>
   *
   * <p>It respects control flow if a resolve candidate is defined in the same scope as the location we resolve the attribute from.</p>
   *
   * <p>Since an attribute doesn't have to be defined in the same method we use it, we have to assume that an attribute we cannot
   * resolve via the control flow graph is defined in some other method. If the attribute is not resolved via the graph, but is defined
   * in a sibling if-elif-else branch, we assume it will become available in our branch eventually in subsequent method calls.</p>
   */
  private static final class PyAttributesProcessor extends PyResolveProcessor {
    private final @Nullable PyExpression myLocation;

    PyAttributesProcessor(@NotNull String name, @Nullable PyExpression location) {
      super(name);
      myLocation = location;
    }

    @Override
    protected boolean tryAddResult(@Nullable PsiElement element, @Nullable PyImportedNameDefiner definer) {
      PsiElement psiElement = definer != null ? definer : element;
      if (element instanceof PyTypeParameter) return false;
      if (inSameScope(psiElement, myLocation)) {
        if (PsiTreeUtil.isAncestor(psiElement, myLocation, false) ||
            PyDefUseUtil.isDefinedBefore(psiElement, myLocation) ||
            inDifferentBranchesOfSameIfStatement(psiElement, myLocation)) {
          if (myOwner == null) {
            myOwner = ScopeUtil.getScopeOwner(psiElement);
          }
          addResult(element, definer);
        }
        return true;
      }
      return super.tryAddResult(element, definer);
    }

    private static boolean inSameScope(@Nullable PsiElement e1, @Nullable PsiElement e2) {
      if (e1 == null || e2 == null) return false;
      ScopeOwner o1 = ScopeUtil.getScopeOwner(e1);
      ScopeOwner o2 = ScopeUtil.getScopeOwner(e2);
      return o1 != null && o1 == o2;
    }

    private static boolean inDifferentBranchesOfSameIfStatement(@NotNull PsiElement e1, @NotNull PsiElement e2) {
      PyIfStatement ifStatement = ObjectUtils.tryCast(PsiTreeUtil.findCommonParent(e1, e2), PyIfStatement.class);
      if (ifStatement == null) return false;
      List<PyStatementPart> parts = getIfStatementParts(ifStatement);
      PyStatementPart p1 = findIfStatementPartByElement(e1, parts);
      PyStatementPart p2 = findIfStatementPartByElement(e2, parts);
      return p1 != p2;
    }

    private static @Nullable PyStatementPart findIfStatementPartByElement(@NotNull PsiElement element, @NotNull List<PyStatementPart> parts) {
      return ContainerUtil.find(parts, part -> PsiTreeUtil.isAncestor(part, element, true));
    }

    private static @NotNull List<PyStatementPart> getIfStatementParts(@NotNull PyIfStatement statement) {
      List<PyStatementPart> parts = new ArrayList<>();
      parts.add(statement.getIfPart());
      parts.addAll(Arrays.asList(statement.getElifParts()));
      PyElsePart elsePart = statement.getElsePart();
      if (elsePart != null) {
        parts.add(elsePart);
      }
      return parts;
    }
  }
}
