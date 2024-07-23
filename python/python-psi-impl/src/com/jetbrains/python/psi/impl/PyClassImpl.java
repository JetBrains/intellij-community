// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.PyAstFunction.Modifier;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.stubs.PyClassElementType;
import com.jetbrains.python.psi.impl.stubs.PyVersionSpecificStubBaseKt;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyDeprecationUtilKt.extractDeprecationMessageFromDecorator;


public class PyClassImpl extends PyBaseElementImpl<PyClassStub> implements PyClass {
  public static class MROException extends Exception {
    public MROException(String s) {
      super(s);
    }
  }

  public static final PyClass[] EMPTY_ARRAY = new PyClassImpl[0];

  @Nullable private volatile List<PyTargetExpression> myInstanceAttributes;
  @Nullable private volatile List<PyTargetExpression> myFallbackInstanceAttributes;
  // Class attributes initialized in @classmethod-decorated methods
  @Nullable private volatile List<PyTargetExpression> myClassAttributesFromClassMethods;

  private volatile Map<String, Property> myLocalPropertyCache;

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return new PyClassTypeImpl(this, true);
  }

  public PyClassImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  public PyClassImpl(@NotNull final PyClassStub stub) {
    this(stub, PyStubElementTypes.CLASS_DECLARATION);
  }

  public PyClassImpl(@NotNull final PyClassStub stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }


  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = PyUtil.createNewName(this, name);
    final ASTNode node = getNameNode();
    if (node != null) {
      getNode().replaceChild(node, nameElement);
    }
    return this;
  }

  @Nullable
  @Override
  public String getName() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      return PyClass.super.getName();
    }
  }

  @Override
  public Icon getIcon(int flags) {
    return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }

  @Override
  public PyExpression @NotNull [] getSuperClassExpressions() {
    final PyArgumentList argList = getSuperClassExpressionList();
    if (argList != null) {
      return argList.getArguments();
    }
    return PyExpression.EMPTY_ARRAY;
  }

  @NotNull
  public static List<PyExpression> getUnfoldedSuperClassExpressions(@NotNull PyClass pyClass) {
    return StreamEx
      .of(pyClass.getSuperClassExpressions())
      .filter(expression -> !(expression instanceof PyKeywordArgument))
      .flatCollection(PyClassImpl::unfoldSuperClassExpression)
      .toList();
  }

  @NotNull
  private static List<PyExpression> unfoldSuperClassExpression(@NotNull PyExpression expression) {
    if (isSixWithMetaclassCall(expression)) {
      final PyExpression[] arguments = ((PyCallExpression)expression).getArguments();
      if (arguments.length > 1) {
        return ContainerUtil.subArrayAsList(arguments, 1, arguments.length);
      }
      else {
        return Collections.emptyList();
      }
    }
    // Heuristic: unfold Foo[Bar] to Foo for subscription expressions for superclasses
    else if (expression instanceof PySubscriptionExpression subscriptionExpr) {
      return Collections.singletonList(subscriptionExpr.getOperand());
    }

    return Collections.singletonList(expression);
  }

  public static boolean isSixWithMetaclassCall(@NotNull PyExpression expression) {
    if (expression instanceof PyCallExpression){
      final PyExpression callee = ((PyCallExpression)expression).getCallee();

      if (callee instanceof PyReferenceExpression) {
        final QualifiedName sixWithMetaclass = QualifiedName.fromComponents("six", "with_metaclass");
        final QualifiedName djangoWithMetaclass = QualifiedName.fromDottedString("django.utils.six.with_metaclass");

        return ContainerUtil.exists(PyResolveUtil.resolveImportedElementQNameLocally((PyReferenceExpression)callee),
                                    name -> name.equals(sixWithMetaclass) || name.equals(djangoWithMetaclass));
      }
    }

    return false;
  }

  @NotNull
  @Override
  public final List<PyClass> getAncestorClasses(@Nullable final TypeEvalContext context) {
    final List<PyClass> results = new ArrayList<>();
    for (final PyClassLikeType type : getAncestorTypes(notNullizeContext(context))) {
      if (type instanceof PyClassType) {
        results.add(((PyClassType)type).getPyClass());
      }
    }
    return results;
  }

  @Override
  public boolean isSubclass(PyClass parent, @Nullable TypeEvalContext context) {
    if (this == parent) {
      return true;
    }
    for (PyClass superclass : getAncestorClasses(context)) {
      if (parent == superclass) return true;
    }
    return false;
  }

  @Override
  public boolean isSubclass(@NotNull String superClassQName, @Nullable TypeEvalContext context) {
    if (superClassQName.equals(getQualifiedName())) {
      return true;
    }
    for (PyClassLikeType type : getAncestorTypes(notNullizeContext(context))) {
      if (type != null && superClassQName.equals(type.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }

  @Override
  @Nullable
  public List<String> getSlots(@Nullable TypeEvalContext context) {
    final Set<String> result = new LinkedHashSet<>();

    final PyClassType currentType = new PyClassTypeImpl(this, true);
    final TypeEvalContext contextToUse = notNullizeContext(context);

    for (PyClassLikeType type : Iterables.concat(Collections.singletonList(currentType), getAncestorTypes(contextToUse))) {
      if (!(type instanceof PyClassType)) return null;

      final PyClass cls = ((PyClassType)type).getPyClass();
      if (PyUtil.isObjectClass(cls)) {
        continue;
      }

      if (!cls.isNewStyleClass(contextToUse)) return null;

      final List<String> ownSlots = cls.getOwnSlots();
      if (ownSlots == null || ownSlots.contains(PyNames.DUNDER_DICT)) {
        return null;
      }

      result.addAll(ownSlots);
    }

    return new ArrayList<>(result);
  }

  @Nullable
  @Override
  public List<String> getOwnSlots() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getSlots();
    }

    final PyTargetExpression slots = ContainerUtil.find(getClassAttributes(), target -> PyNames.SLOTS.equals(target.getName()));
    if (slots != null) {
      final PyExpression value = slots.findAssignedValue();

      return value instanceof PyStringLiteralExpression
             ? Collections.singletonList(((PyStringLiteralExpression)value).getStringValue())
             : PyUtilCore.strListValue(value);
    }

    return null;
  }

  @Override
  public PyClass @NotNull [] getSuperClasses(@Nullable TypeEvalContext context) {
    final List<PyClassLikeType> superTypes = getSuperClassTypes(notNullizeContext(context));
    if (superTypes.isEmpty()) {
      return EMPTY_ARRAY;
    }
    final List<PyClass> result = new ArrayList<>();
    for (PyClassLikeType type : superTypes) {
      if (type instanceof PyClassType) {
        result.add(((PyClassType)type).getPyClass());
      }
    }
    return result.toArray(PyClass.EMPTY_ARRAY);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this) {
      @NotNull
      @Override
      public String getPresentableText() {
        PyPsiUtils.assertValid(PyClassImpl.this);
        final StringBuilder result = new StringBuilder(notNullize(getName(), PyNames.UNNAMED_ELEMENT));
        final List<String> superClassesText = getSuperClassesText();
        if (!superClassesText.isEmpty()) {
          result.append("(");
          result.append(join(superClassesText, expr -> notNullize(expr, PyNames.UNNAMED_ELEMENT), ", "));
          result.append(")");
        }
        return result.toString();
      }
    };
  }

  private List<String> getSuperClassesText() {
    PyClassStub stub = getGreenStub();
    if (stub == null) {
      return ContainerUtil.map(getSuperClassExpressions(), PsiElement::getText);
    }
    else {
      return stub.getSuperClassesText();
    }
  }

  @NotNull
  private static List<PyClassLikeType> mroMerge(@NotNull List<List<PyClassLikeType>> sequences) throws MROException {
    List<PyClassLikeType> result = new LinkedList<>(); // need to insert to 0th position on linearize
    while (true) {
      // filter blank sequences
      final List<List<PyClassLikeType>> nonBlankSequences = new ArrayList<>(sequences.size());
      for (List<PyClassLikeType> item : sequences) {
        if (!item.isEmpty()) nonBlankSequences.add(item);
      }
      if (nonBlankSequences.isEmpty()) return result;
      // find a clean head
      boolean found = false;
      PyClassLikeType head = null; // to keep compiler happy; really head is assigned in the loop at least once.
      for (List<PyClassLikeType> seq : nonBlankSequences) {
        head = seq.get(0);
        if (head == null) {
          seq.remove(0);
          found = true;
          break;
        }
        boolean headInTails = false;
        for (List<PyClassLikeType> tailSeq : nonBlankSequences) {
          if (tailSeq.indexOf(head) > 0) { // -1 is not found, 0 is head, >0 is tail.
            headInTails = true;
            break;
          }
        }
        if (!headInTails) {
          found = true;
          break;
        }
        else {
          head = null; // as a signal
        }
      }
      if (!found) {
        // Inconsistent hierarchy results in TypeError
        throw new MROException("Inconsistent class hierarchy");
      }
      // our head is clean;
      result.add(head);
      // remove it from heads of other sequences
      if (head != null) {
        for (List<PyClassLikeType> seq : nonBlankSequences) {
          if (Comparing.equal(seq.get(0), head)) {
            seq.remove(0);
          }
        }
      }
    } // we either return inside the loop or die by assertion
  }


  @NotNull
  private static List<PyClassLikeType> mroLinearize(@NotNull PyClassLikeType type,
                                                    boolean addThisType,
                                                    @NotNull TypeEvalContext context,
                                                    @NotNull Map<PyClassLikeType, Ref<List<PyClassLikeType>>> cache) throws MROException {
    final Ref<List<PyClassLikeType>> computed = cache.get(type);
    if (computed != null) {
      if (computed.isNull()) {
        throw new MROException("Circular class inheritance");
      }
      return computed.get();
    }
    cache.put(type, Ref.create());
    List<PyClassLikeType> result = null;
    try {
      final List<PyClassLikeType> bases = removeNotNullDuplicates(type.getSuperClassTypes(context));
      final List<List<PyClassLikeType>> lines = new ArrayList<>();
      for (PyClassLikeType base : bases) {
        if (base != null) {
          // Don't include ancestors of a metaclass instance
          final List<PyClassLikeType> baseClassMRO;
          if (base.isDefinition()) {
            baseClassMRO = mroLinearize(base, true, context, cache);
          }
          else {
            List<PyClassLikeType> metaclassInstanceMro = new ArrayList<>();
            metaclassInstanceMro.add(base);
            if (base instanceof PyClassType) {
              final PyClassImpl pyClass = as(((PyClassType)base).getPyClass(), PyClassImpl.class);
              if (pyClass != null) {
                ContainerUtil.addIfNotNull(metaclassInstanceMro, pyClass.getImplicitSuper());
              }
            }
            baseClassMRO = metaclassInstanceMro;
          }
          if (!baseClassMRO.isEmpty()) {
            // mroMerge() updates passed MRO lists internally
            lines.add(new LinkedList<>(baseClassMRO));
          }
        }
      }
      if (!bases.isEmpty()) {
        lines.add(bases);
      }
      result = mroMerge(lines);
      if (addThisType) {
        result.add(0, type);
      }
      result = Collections.unmodifiableList(result);
    }
    finally {
      cache.put(type, Ref.create(result));
    }
    return result;
  }

  @NotNull
  private static <T> List<T> removeNotNullDuplicates(@NotNull List<T> list) {
    final Set<T> distinct = new HashSet<>();
    final List<T> result = new ArrayList<>();
    for (T elem : list) {
      if (elem != null) {
        final boolean isUnique = distinct.add(elem);
        if (!isUnique) {
          continue;
        }
      }
      result.add(elem);
    }
    return result;
  }

  @Override
  public PyFunction @NotNull [] getMethodsInherited(@Nullable TypeEvalContext context) {
    List<PyFunction> collectedMethods = new ArrayList<>(Arrays.asList(getMethods()));

    for (PyClass superClass : getAncestorClasses(context)) {
      collectedMethods.addAll(Arrays.asList(superClass.getMethods()));
    }
    return collectedMethods.toArray(PyFunction.EMPTY_ARRAY);
  }

  @Override
  public PyFunction @NotNull [] getMethods() {
    final TokenSet functionDeclarationTokens = PythonDialectsTokenSetProvider.getInstance().getFunctionDeclarationTokens();
    return getClassChildren(functionDeclarationTokens, PyFunction.class, PyFunction.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public Map<String, Property> getPropertiesInherited(@Nullable TypeEvalContext context) {
    initLocalProperties();
    Map<String, Property> propertiesHashMap = new HashMap<>(myLocalPropertyCache);

    for (PyClass superClass : getAncestorClasses(context)) {
      Map<String, Property> superClassProperties = superClass.getPropertiesInherited(context);
      for (Map.Entry<String, Property> entry : superClassProperties.entrySet()) {
        // Do not replace existing property in case superclass have it, keep only subclass properties
        propertiesHashMap.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }
    return propertiesHashMap;
  }

  @Override
  @NotNull
  public Map<String, Property> getProperties() {
    initLocalProperties();
    return new HashMap<>(myLocalPropertyCache);
  }

  @Override
  public PyClass[] getNestedClasses() {
    return getClassChildren(TokenSet.create(PyElementTypes.CLASS_DECLARATION), PyClass.class, PyClass.ARRAY_FACTORY);
  }

  private <T extends StubBasedPsiElement<? extends StubElement<T>>> T @NotNull [] getClassChildren(@NotNull TokenSet elementTypes,
                                                                                                   @NotNull Class<T> childrenClass,
                                                                                                   @NotNull ArrayFactory<? extends T> factory) {
    final List<T> result = new ArrayList<>();
    processClassLevelDeclarations(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (childrenClass.isInstance(element) && elementTypes.contains(((StubBasedPsiElement<?>)element).getElementType())) {
          result.add(childrenClass.cast(element));
        }
        return true;
      }
    });
    return ContainerUtil.toArray(result, factory);
  }

  private static class NameFinder<T extends PyElement> implements Processor<T> {
    @NotNull
    private final TypeEvalContext myContext;
    private T myResult;
    private final String[] myNames;
    private int myLastResultIndex = -1;
    private PyClass myLastVisitedClass = null;

    NameFinder(@NotNull TypeEvalContext context, String... names) {
      myContext = context;
      myNames = names;
      myResult = null;
    }

    public T getResult() {
      return myResult;
    }

    @Nullable
    protected PyClass getContainingClass(@NotNull T element) {
      return null;
    }

    @Override
    public boolean process(T target) {
      final PyClass currentClass = getContainingClass(target);
      // Stop when the current class changes and there was a result
      if (myLastVisitedClass != null && myLastVisitedClass != currentClass && myResult != null) {
        return false;
      }

      myLastVisitedClass = currentClass;

      final int index = ArrayUtil.indexOf(myNames, target.getName());
      // Do not depend on the order in which elements appear, always try to find the first one
      if (index >= 0) {
        if (myLastResultIndex == -1 ||
            index < myLastResultIndex ||
            index == myLastResultIndex && PyiUtil.isOverload(myResult, myContext) && !PyiUtil.isOverload(target, myContext)) {
          myLastResultIndex = index;
          myResult = target;

          if (index == 0 && !PyiUtil.isOverload(myResult, myContext)) {
            return false;
          }
        }
      }
      return true;
    }
  }

  private static class MultiNameFinder<T extends PyElement> implements Processor<T> {

    @NotNull
    private final List<T> myResult;

    private final String @NotNull [] myNames;

    @Nullable
    private PyClass myLastVisitedClass;

    MultiNameFinder(String @NotNull ... names) {
      myResult = new ArrayList<>();
      myNames = names;
      myLastVisitedClass = null;
    }

    @Override
    public boolean process(T t) {
      final PyClass currentClass = t instanceof PyPossibleClassMember ? ((PyPossibleClassMember)t).getContainingClass() : null;
      // Stop when the current class changes and there was a result
      if (myLastVisitedClass != null && currentClass != myLastVisitedClass && !myResult.isEmpty()) {
        return false;
      }

      myLastVisitedClass = currentClass;

      if (ArrayUtil.contains(t.getName(), myNames)) {
        myResult.add(t);
      }

      return true;
    }
  }

  @Override
  public PyFunction findMethodByName(@Nullable final String name, boolean inherited, @Nullable TypeEvalContext context) {
    if (name == null) return null;
    NameFinder<PyFunction> proc = new NameFinder<>(notNullizeContext(context), name);
    visitMethods(proc, inherited, context);
    return proc.getResult();
  }

  @NotNull
  @Override
  public List<PyFunction> multiFindMethodByName(@NotNull String name, boolean inherited, @Nullable TypeEvalContext context) {
    final MultiNameFinder<PyFunction> processor = new MultiNameFinder<>(name);
    visitMethods(processor, inherited, context);
    return processor.myResult;
  }

  @Nullable
  @Override
  public PyClass findNestedClass(String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyClass> proc = new NameFinder<>(TypeEvalContext.codeInsightFallback(getProject()), name);
    visitNestedClasses(proc, inherited);
    return proc.getResult();
  }

  @Nullable
  @Override
  public PyFunction findInitOrNew(boolean inherited, final @Nullable TypeEvalContext context) {
    NameFinder<PyFunction> proc;
    if (isNewStyleClass(context)) {
      proc = new NameFinder<>(notNullizeContext(context), PyNames.INIT, PyNames.NEW) {
        @Nullable
        @Override
        protected PyClass getContainingClass(@NotNull PyFunction element) {
          return element.getContainingClass();
        }
      };
    }
    else {
      proc = new NameFinder<>(notNullizeContext(context), PyNames.INIT);
    }
    visitMethods(proc, inherited, context);
    return proc.getResult();
  }

  @NotNull
  @Override
  public List<PyFunction> multiFindInitOrNew(boolean inherited, @Nullable TypeEvalContext context) {
    final MultiNameFinder<PyFunction> processor = isNewStyleClass(context)
      ? new MultiNameFinder<>(PyNames.INIT, PyNames.NEW)
      : new MultiNameFinder<>(PyNames.INIT);

    visitMethods(processor, inherited, context);
    return processor.myResult;
  }

  private final static Maybe<PyCallable> UNKNOWN_CALL = new Maybe<>(); // denotes _not_ a PyFunction, actually
  private final static Maybe<PyCallable> NONE = new Maybe<>(null); // denotes an explicit None

  /**
   * @param filter returns true if the property is acceptable
   * @return the first property that filter accepted.
   */
  @Nullable
  private Property processPropertiesInClass(@Nullable Processor<? super Property> filter) {
    final Property decoratedProperty = processDecoratedProperties(filter);
    if (decoratedProperty != null) return decoratedProperty;

    if (getStub() != null) {
      return processStubProperties(filter);
    }
    else {
      // name = property(...) assignments from PSI
      for (PyTargetExpression target : getClassAttributes()) {
        final Property property = PropertyImpl.fromTarget(target);
        if (property != null && (filter == null || filter.process(property))) {
          return property;
        }
      }
    }

    return null;
  }

  @Nullable
  private Property processDecoratedProperties(@Nullable Processor<? super Property> filter) {
    // look at @property decorators
    final MultiMap<String, PyFunction> grouped = new MultiMap<>();
    // group suitable same-named methods, each group defines a property
    for (PyFunction method : getMethods()) {
      grouped.putValue(method.getName(), method);
    }
    for (Map.Entry<String, Collection<PyFunction>> entry : grouped.entrySet()) {
      Maybe<PyCallable> getter = NONE;
      Maybe<PyCallable> setter = NONE;
      Maybe<PyCallable> deleter = NONE;
      final String decoratorName = entry.getKey();
      for (PyFunction method : entry.getValue()) {
        final PyDecoratorList decoratorList = method.getDecoratorList();
        if (decoratorList != null) {
          for (PyDecorator deco : decoratorList.getDecorators()) {
            final QualifiedName qname = deco.getQualifiedName();
            if (qname != null) {
              String decoName = qname.toString();
              for (PyKnownDecoratorProvider provider : PyKnownDecoratorProvider.EP_NAME.getExtensionList()) {
                final String knownName = provider.toKnownDecorator(decoName);
                if (knownName != null) {
                  decoName = knownName;
                }
              }
              if (PyNames.PROPERTY.equals(decoName) ||
                  PyKnownDecoratorUtil.isPropertyDecorator(deco, TypeEvalContext.codeInsightFallback(getProject())) ||
                  qname.matches(decoratorName, PyNames.GETTER)) {
                getter = new Maybe<>(method);
              }
              else if (qname.matches(decoratorName, PyNames.SETTER)) {
                setter = new Maybe<>(method);
              }
              else if (qname.matches(decoratorName, PyNames.DELETER)) {
                deleter = new Maybe<>(method);
              }
            }
          }
        }
        if (getter != NONE && setter != NONE && deleter != NONE) break; // can't improve
      }
      if (getter != NONE || setter != NONE || deleter != NONE) {
        final PropertyImpl prop = new PropertyImpl(decoratorName, getter, setter, deleter, null, null);
        if (filter == null || filter.process(prop)) return prop;
      }
    }
    return null;
  }

  private Maybe<PyCallable> fromPacked(Maybe<String> maybeName) {
    if (maybeName.isDefined()) {
      final String value = maybeName.value();
      if (value == null || PyNames.NONE.equals(value)) {
        return NONE;
      }
      PyFunction method = findMethodByName(value, true, null);
      if (method != null) return new Maybe<>(method);
    }
    return UNKNOWN_CALL;
  }

  @Nullable
  private Property processStubProperties(@Nullable Processor<? super Property> filter) {
    final PyClassStub stub = getStub();
    if (stub != null) {
      LanguageLevel languageLevel = PyiUtil.getOriginalLanguageLevel(this);
      for (StubElement<?> subStub : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
        if (subStub.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
          final PyTargetExpressionStub targetStub = (PyTargetExpressionStub)subStub;
          final PropertyStubStorage prop = targetStub.getCustomStub(PropertyStubStorage.class);
          if (prop != null) {
            final Maybe<PyCallable> getter = fromPacked(prop.getGetter());
            final Maybe<PyCallable> setter = fromPacked(prop.getSetter());
            final Maybe<PyCallable> deleter = fromPacked(prop.getDeleter());
            final String doc = prop.getDoc();
            if (getter != NONE || setter != NONE || deleter != NONE) {
              final PropertyImpl property = new PropertyImpl(targetStub.getName(), getter, setter, deleter, doc, targetStub.getPsi());
              if (filter == null || filter.process(property)) return property;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Property findProperty(@NotNull String name, boolean inherited, @Nullable TypeEvalContext context) {
    initLocalProperties();

    final Property property = myLocalPropertyCache.get(name);
    if (property != null) {
      return property;
    }

    if (findMethodByName(name, false, context) != null ||
        findClassAttribute(name, false, context) != null ||
        ContainerUtil.notNullize(getOwnSlots()).contains(name)) {
      return null;
    }

    if (inherited) {
      for (PyClass cls : getAncestorClasses(context)) {
        final Property ancestorProperty = cls.findProperty(name, false, context);
        if (ancestorProperty != null) {
          return ancestorProperty;
        }
      }
    }

    return null;
  }

  @Override
  @Nullable
  public Property findPropertyByCallable(PyCallable callable) {
    initLocalProperties();

    for (Property property : myLocalPropertyCache.values()) {
      if (property.getGetter().valueOrNull() == callable ||
          property.getSetter().valueOrNull() == callable ||
          property.getDeleter().valueOrNull() == callable) {
        return property;
      }
    }

    return null;
  }

  private synchronized void initLocalProperties() {
    if (myLocalPropertyCache == null) {
      final Map<String, Property> result = new HashMap<>();

      processProperties(
        property -> {
          result.put(property.getName(), property);
          return false;
        },
        false
      );

      myLocalPropertyCache = result;
    }
  }

  @Nullable
  @Override
  public Property scanProperties(Processor<? super Property> filter, boolean inherited) {
    return processProperties(filter, inherited);
  }

  @Nullable
  private Property processProperties(@Nullable Processor<? super Property> filter, boolean inherited) {
    PyPsiUtils.assertValid(this);

    final Property local = processPropertiesInClass(filter);
    if (local != null) {
      return local;
    }

    if (inherited) {
      for (PyClass cls : getAncestorClasses(null)) {
        final Property property = ((PyClassImpl)cls).processPropertiesInClass(filter);
        if (property != null) {
          return property;
        }
      }
    }

    return null;
  }

  private static final class PropertyImpl extends PropertyBunch<PyCallable> implements Property {
    private final String myName;

    private PropertyImpl(String name,
                         Maybe<PyCallable> getter,
                         Maybe<PyCallable> setter,
                         Maybe<PyCallable> deleter,
                         String doc,
                         PyTargetExpression site) {
      myName = name;
      myDeleter = deleter;
      myGetter = getter;
      mySetter = setter;
      myDoc = doc;
      mySite = site;
    }

    @NotNull
    @Override
    public Maybe<PyCallable> getGetter() {
      return filterNonStubExpression(myGetter);
    }

    @NotNull
    @Override
    public Maybe<PyCallable> getSetter() {
      return filterNonStubExpression(mySetter);
    }

    @NotNull
    @Override
    public Maybe<PyCallable> getDeleter() {
      return filterNonStubExpression(myDeleter);
    }

    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public PyTargetExpression getDefinitionSite() {
      return mySite;
    }

    @NotNull
    @Override
    public Maybe<PyCallable> getByDirection(@NotNull AccessDirection direction) {
      return switch (direction) {
        case READ -> getGetter();
        case WRITE -> getSetter();
        case DELETE -> getDeleter();
      };
    }

    @Nullable
    @Override
    public PyType getType(@Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
      if (mySite instanceof PyTargetExpressionImpl) {
        final PyType targetDocStringType = ((PyTargetExpressionImpl)mySite).getTypeFromDocString();
        if (targetDocStringType != null) {
          return targetDocStringType;
        }
      }
      final PyCallable callable = myGetter.valueOrNull();
      if (callable != null) {
        // Ignore return types of non stub-based elements if we are not allowed to use AST
        if (!(callable instanceof StubBasedPsiElement) && !context.maySwitchToAST(callable)) {
          return null;
        }
        return callable.getCallType(receiver, buildArgumentsToParametersMap(receiver, callable, context), context);
      }
      return null;
    }

    @NotNull
    private static Map<PyExpression, PyCallableParameter> buildArgumentsToParametersMap(@Nullable PyExpression receiver,
                                                                                        @NotNull PyCallable callable,
                                                                                        @NotNull TypeEvalContext context) {
      if (receiver == null) return Collections.emptyMap();

      final PyCallableParameter firstParameter = ContainerUtil.getFirstItem(callable.getParameters(context));
      if (firstParameter == null || !firstParameter.isSelf()) return Collections.emptyMap();

      return ImmutableMap.of(receiver, firstParameter);
    }

    @NotNull
    @Override
    protected Maybe<PyCallable> translate(@Nullable PyExpression expr) {
      if (expr == null || expr instanceof PyNoneLiteralExpression) {
        return NONE;
      }
      if (expr instanceof PyCallable) {
        return new Maybe<>((PyCallable)expr);
      }
      final PsiReference ref = expr.getReference();
      if (ref != null) {
        PsiElement something = ref.resolve();
        if (something instanceof PyCallable) {
          return new Maybe<>((PyCallable)something);
        }
      }
      return NONE;
    }

    @NotNull
    private static Maybe<PyCallable> filterNonStubExpression(@NotNull Maybe<PyCallable> maybeCallable) {
      final PyCallable callable = maybeCallable.valueOrNull();
      if (callable != null) {
        if (!(callable instanceof StubBasedPsiElement)) {
          return UNKNOWN_CALL;
        }
      }
      return maybeCallable;
    }

    @Override
    public String toString() {
      return "property(" + myGetter + ", " + mySetter + ", " + myDeleter + ", " + myDoc + ")";
    }

    @Nullable
    public static PropertyImpl fromTarget(PyTargetExpression target) {
      PyExpression expr = target.findAssignedValue();
      final PropertyImpl prop = new PropertyImpl(target.getName(), null, null, null, null, target);
      final boolean success = fillFromCall(expr, prop);
      return success ? prop : null;
    }
  }

  @Override
  public boolean visitMethods(Processor<? super PyFunction> processor, boolean inherited, @Nullable final TypeEvalContext context) {
    if (!ContainerUtil.process(getMethods(), processor)) return false;
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses(context)) {
        if (!ancestor.visitMethods(processor, false, context)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitNestedClasses(Processor<? super PyClass> processor, boolean inherited) {
    PyClass[] nestedClasses = getNestedClasses();
    if (!ContainerUtil.process(nestedClasses, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses(null)) {
        if (!((PyClassImpl)ancestor).visitNestedClasses(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public boolean visitClassAttributes(Processor<? super PyTargetExpression> processor, boolean inherited, @Nullable final TypeEvalContext context) {
    List<PyTargetExpression> methods = getClassAttributes();
    if (!ContainerUtil.process(methods, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses(context)) {
        if (!ancestor.visitClassAttributes(processor, false, context)) {
          return false;
        }
      }
    }
    return true;
    // NOTE: sorry, not enough metaprogramming to generalize visitMethods and visitClassAttributes
  }

  @NotNull
  @Override
  public final List<PyTargetExpression> getClassAttributesInherited(@NotNull final TypeEvalContext context) {
    final MyAttributesCollector attributesCollector = new MyAttributesCollector();
    visitClassAttributes(attributesCollector, true, context);
    return attributesCollector.getAttributes();
  }

  @Override
  public List<PyTargetExpression> getClassAttributes() {
    final List<PyTargetExpression> result = new ArrayList<>();
    LanguageLevel languageLevel = PyiUtil.getOriginalLanguageLevel(this);
    PyClassStub stub = getStub();
    if (stub != null) {
      for (StubElement<?> element : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
        if (element.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
          result.add((PyTargetExpression)element.getPsi());
        }
      }
    }
    else {
      getStatementList().acceptChildren(new PyVersionAwareTopLevelElementVisitor(languageLevel) {
        @Override
        protected void checkAddElement(PsiElement psiElement) {
          if (psiElement instanceof PyAssignmentStatement assignmentStatement) {
            final PyExpression[] targets = assignmentStatement.getTargets();
            for (PyExpression target : targets) {
              if (target instanceof PyTargetExpression) {
                result.add((PyTargetExpression)target);
              }
            }
          }
          else if (psiElement instanceof PyTypeDeclarationStatement) {
            final PyExpression target = ((PyTypeDeclarationStatement)psiElement).getTarget();
            if (target instanceof PyTargetExpression) {
              result.add((PyTargetExpression)target);
            }
          }
        }
      });
    }
    return result;
  }

  @Override
  public PyTargetExpression findClassAttribute(@NotNull String name, boolean inherited, TypeEvalContext context) {
    final NameFinder<PyTargetExpression> processor = new NameFinder<>(notNullizeContext(context), name);
    visitClassAttributes(processor, inherited, context);
    return processor.getResult();
  }

  @NotNull
  @Override
  public List<PyTargetExpression> getInstanceAttributes() {
    List<PyTargetExpression> attributes = myInstanceAttributes;
    if (attributes != null) {
      return attributes;
    }
    attributes = collectInstanceAttributes(Collections.emptyMap());
    myInstanceAttributes = attributes;
    return attributes;
  }

  @Nullable
  @Override
  public PyTargetExpression findInstanceAttribute(String name, boolean inherited) {
    final List<PyTargetExpression> instanceAttributes = getInstanceAttributes();
    for (PyTargetExpression instanceAttribute : instanceAttributes) {
      if (name.equals(instanceAttribute.getReferencedName())) {
        return instanceAttribute;
      }
    }
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses(null)) {
        final PyTargetExpression attribute = ancestor.findInstanceAttribute(name, false);
        if (attribute != null) {
          return attribute;
        }
      }
    }
    return null;
  }

  @NotNull
  private List<PyTargetExpression> getFallbackInstanceAttributes() {
    List<PyTargetExpression> attributes = myFallbackInstanceAttributes;
    if (attributes != null) {
      return attributes;
    }
    Map<String, ScopeOwner> scopesToSkip = StreamEx.of(getInstanceAttributes())
      .filter(e -> e.getName() != null)
      .mapToEntry(e -> e.getName(), e -> ScopeUtil.getScopeOwner(e))
      .toMap();
    attributes = collectInstanceAttributes(scopesToSkip);
    myFallbackInstanceAttributes = attributes;
    return attributes;
  }

  @NotNull
  private List<PyTargetExpression> collectInstanceAttributes(@NotNull Map<String, ScopeOwner> scopesToSkip) {
    Map<String, PyTargetExpression> result = new HashMap<>();
    collectAttributesInConstructors(result, scopesToSkip);
    final PyFunction[] methods = getMethods();
    for (PyFunction method : methods) {
      collectInstanceAttributes(method, result, result.keySet(), scopesToSkip);
    }
    return new ArrayList<>(result.values());
  }

  private void collectAttributesInConstructors(@NotNull Map<String, PyTargetExpression> result,
                                               @NotNull Map<String, ScopeOwner> scopesToSkip) {
    PyFunction newMethod = findMethodByName(PyNames.NEW, false, null);
    if (newMethod != null) {
      for (PyTargetExpression target : getTargetExpressions(newMethod)) {
        String name = target.getName();
        if (scopesToSkip.get(name) != newMethod) {
          result.put(name, target);
        }
      }
    }
    PyFunction initMethod = findMethodByName(PyNames.INIT, false, null);
    if (initMethod != null) {
      collectInstanceAttributes(initMethod, result, Collections.emptySet(), scopesToSkip);
    }
  }

  public static void collectInstanceAttributes(@NotNull PyFunction method, @NotNull final Map<String, PyTargetExpression> result) {
    collectInstanceAttributes(method, result, Collections.emptySet(), Collections.emptyMap());
  }

  private static void collectInstanceAttributes(@NotNull PyFunction method,
                                                @NotNull final Map<String, PyTargetExpression> result,
                                                @NotNull Set<String> namesToSkip,
                                                @NotNull Map<String, ScopeOwner> scopesToSkip) {
    final PyParameter[] params = method.getParameterList().getParameters();
    if (params.length == 0) {
      return;
    }
    for (PyTargetExpression target : getTargetExpressions(method)) {
      String name = target.getName();
      if (!namesToSkip.contains(name) &&
          scopesToSkip.get(name) != method &&
          PyUtil.isInstanceAttribute(target)) {
        result.put(name, target);
      }
    }
  }

  @NotNull
  private static List<PyTargetExpression> getTargetExpressions(@NotNull PyFunction function) {
    final PyFunctionStub stub = function.getStub();
    if (stub != null) {
      return Arrays.asList(stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY));
    }
    else {
      final PyStatementList statementList = function.getStatementList();
      final List<PyTargetExpression> result = new ArrayList<>();
      statementList.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyClass(@NotNull PyClass node) {
        }

        @Override
        public void visitPyAssignmentStatement(final @NotNull PyAssignmentStatement node) {
          for (PyExpression expression : node.getTargets()) {
            if (expression instanceof PyTargetExpression) {
              result.add((PyTargetExpression)expression);
            }
          }
        }

        @Override
        public void visitPyWithStatement(@NotNull PyWithStatement node) {
          StreamEx
            .of(node.getWithItems())
            .map(PyWithItem::getTarget)
            .select(PyTargetExpression.class)
            .forEach(result::add);

          super.visitPyWithStatement(node);
        }
      });
      return result;
    }
  }

  @Override
  public boolean isNewStyleClass(@Nullable TypeEvalContext context) {
    return NotNullLazyValue.<ParameterizedCachedValue<Boolean, TypeEvalContext>>lazy(() -> {
      return CachedValuesManager.getManager(getProject())
        .createParameterizedCachedValue(param -> new Result<>(calculateNewStyleClass(param), PsiModificationTracker.MODIFICATION_COUNT), false);
    }).getValue().getValue(context);
  }

  private boolean calculateNewStyleClass(@Nullable TypeEvalContext context) {
    final PsiFile containingFile = getContainingFile();
    if (containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isPy3K()) {
      return true;
    }
    final PyClass objClass = PyBuiltinCache.getInstance(this).getClass(PyNames.OBJECT);
    if (this == objClass) return true; // a rare but possible case
    if (hasNewStyleMetaClass(this)) return true;
    for (PyClassLikeType type : getOldStyleAncestorTypes(notNullizeContext(context))) {
      if (type == null) {
        // unknown, assume new-style class
        return true;
      }
      if (type instanceof PyClassType) {
        final PyClass pyClass = ((PyClassType)type).getPyClass();
        if (pyClass == objClass || hasNewStyleMetaClass(pyClass)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasNewStyleMetaClass(PyClass pyClass) {
    final PsiFile containingFile = pyClass.getContainingFile();
    if (containingFile instanceof PyFile) {
      final PsiElement element = ((PyFile)containingFile).getElementNamed(PyNames.DUNDER_METACLASS);
      if (element instanceof PyTargetExpression) {
        final QualifiedName qName = ((PyTargetExpression)element).getAssignedQName();
        if (qName != null && qName.matches("type")) {
          return true;
        }
      }
    }
    if (pyClass.findClassAttribute(PyNames.DUNDER_METACLASS, false, null) != null) {
      return true;
    }
    return false;
  }

  @Override
  public boolean processClassLevelDeclarations(@NotNull PsiScopeProcessor processor) {
    final PyClassStub stub = getStub();
    if (stub != null) {
      LanguageLevel languageLevel = PyiUtil.getOriginalLanguageLevel(this);
      for (StubElement<?> child : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
        if (!processor.execute(child.getPsi(), ResolveState.initial())) {
          return false;
        }
      }
    }
    else {
      PyResolveUtil.scopeCrawlUp(processor, this, null, this);
    }
    return true;
  }

  @Override
  public boolean processClassObjectAttributes(@NotNull PsiScopeProcessor processor, @Nullable PsiElement location) {
    if (!processClassLevelDeclarations(processor)) return false;
    PyFunction containingMethod = PsiTreeUtil.getStubOrPsiParentOfType(location, PyFunction.class);
    PyClass containingClass = containingMethod != null ? containingMethod.getContainingClass() : null;
    boolean isClassMethod = containingMethod != null && containingMethod.getModifier() == Modifier.CLASSMETHOD;
    List<PyTargetExpression> allClassAttributes = getClassAttributesDefinedInClassMethods();
    List<PyTargetExpression> prioritizedClassAttrs;
    if (isClassMethod && containingClass != null && CompletionUtilCoreImpl.getOriginalElement(containingClass) == this) {
      List<PyTargetExpression> sameMethodClassAttrs = ContainerUtil.filter(allClassAttributes,
                                                                           attr -> ScopeUtil.getScopeOwner(attr) == containingMethod);
      List<PyTargetExpression> otherMethodClassAttrs = ContainerUtil.filter(allClassAttributes,
                                                                            attr -> ScopeUtil.getScopeOwner(attr) != containingMethod);
      prioritizedClassAttrs = ContainerUtil.concat(sameMethodClassAttrs, otherMethodClassAttrs);
    }
    else {
      prioritizedClassAttrs = allClassAttributes;
    }
    for (PyTargetExpression target : prioritizedClassAttrs) {
      if (!processor.execute(target, ResolveState.initial())) {
        return false;
      }
    }
    return true;
  }

  private @NotNull List<PyTargetExpression> getClassAttributesDefinedInClassMethods() {
    List<PyTargetExpression> classAttrs = myClassAttributesFromClassMethods;
    if (classAttrs == null) {
      classAttrs = new ArrayList<>();
      for (PyFunction method : getMethods()) {
        if (method.getModifier() == Modifier.CLASSMETHOD) {
          for (PyTargetExpression target : getTargetExpressions(method)) {
            if (PyUtil.isInstanceAttribute(target)) {
              classAttrs.add(target);
            }
          }
        }
      }
      myClassAttributesFromClassMethods = classAttrs;
    }
    return classAttrs;
  }

  @Override
  public boolean processInstanceLevelDeclarations(@NotNull PsiScopeProcessor processor, @Nullable PsiElement location) {
    final PyFunction instanceMethod = PsiTreeUtil.getStubOrPsiParentOfType(location, PyFunction.class);
    final PyClass containingClass = instanceMethod != null ? instanceMethod.getContainingClass() : null;
    if (instanceMethod != null && containingClass != null && CompletionUtilCoreImpl.getOriginalElement(containingClass) == this) {
      for (PyTargetExpression target : getTargetExpressions(instanceMethod)) {
        if (PyUtil.isInstanceAttribute(target) && !processor.execute(target, ResolveState.initial())) {
          return false;
        }
      }
    }
    if (!processInstanceAttributesNotInMethod(processor, instanceMethod, getInstanceAttributes())) return false;
    if (!processInstanceAttributesNotInMethod(processor, instanceMethod, getFallbackInstanceAttributes())) return false;
    return true;
  }

  private static boolean processInstanceAttributesNotInMethod(@NotNull PsiScopeProcessor processor,
                                                              @Nullable PyFunction instanceMethod,
                                                              @NotNull List<PyTargetExpression> instanceAttributes) {
    for (PyTargetExpression expr : instanceAttributes) {
      if (instanceMethod != null && ScopeUtil.getScopeOwner(expr) == instanceMethod) {
        continue;
      }
      if (!processor.execute(expr, ResolveState.initial())) return false;
    }
    return true;
  }

  @Override
  public int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  @Override
  public String getDocStringValue() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return PyClass.super.getDocStringValue();
  }

  @Override
  public @Nullable String getDeprecationMessage() {
    PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getDeprecationMessage();
    }
    return extractDeprecationMessageFromDecorator(this);
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return DocStringUtil.getStructuredDocString(this);
  }

  @Override
  public String toString() {
    return "PyClass: " + getName();
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
    myInstanceAttributes = null;
    myFallbackInstanceAttributes = null;
    myClassAttributesFromClassMethods = null;
    myLocalPropertyCache = null;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
    if (scopeOwner instanceof PyFunction) {
      return new LocalSearchScope(scopeOwner);
    }
    return super.getUseScope();
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getSuperClassTypes(@NotNull final TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(this, context, this::doGetSuperClassTypes);
  }

  @NotNull
  private List<PyClassLikeType> doGetSuperClassTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> result = new ArrayList<>();

    // In some cases stub may not provide all information, so we use stubs only if AST access id disabled
    if (!context.maySwitchToAST(this)) {
      fillSuperClassesNoSwitchToAst(context, getStub(), result);
    }
    else {
      fillSuperClassesSwitchingToAst(context, result);
    }

    PyPsiUtils.assertValid(this);
    if (result.isEmpty()) {
      return Optional
        .ofNullable(getImplicitSuper())
        .map(Collections::singletonList)
        .orElse(Collections.emptyList());
    }

    return result;
  }

  private void fillSuperClassesSwitchingToAst(@NotNull TypeEvalContext context, List<PyClassLikeType> result) {
    for (PyExpression expression : getUnfoldedSuperClassExpressions(this)) {
      final PyType type = context.getType(expression);
      PyClassLikeType classLikeType = null;
      if (type instanceof PyClassLikeType) {
        classLikeType = (PyClassLikeType)type;
      }
      else {
        final PyReferenceExpression referenceExpr = as(expression, PyReferenceExpression.class);
        final PsiElement resolved;
        if (referenceExpr != null) {
          resolved = referenceExpr.followAssignmentsChain(PyResolveContext.defaultContext(context)).getElement();
        }
        else {
          final PsiReference ref = expression.getReference();
          resolved = ref != null ? ref.resolve() : null;
        }
        if (resolved instanceof PyClass) {
          final PyType resolvedType = context.getType((PyClass)resolved);
          if (resolvedType instanceof PyClassLikeType) {
            classLikeType = (PyClassLikeType)resolvedType;
          }
        }
      }
      result.add(classLikeType);
    }
  }

  private void fillSuperClassesNoSwitchToAst(@NotNull final TypeEvalContext context,
                                             @Nullable final PyClassStub stub,
                                             final @NotNull List<? super PyClassLikeType> result) {
    final Map<QualifiedName, QualifiedName> superClasses = stub != null
                                                           ? stub.getSuperClasses()
                                                           : PyClassElementType.getSuperClassQNames(this);

    final PsiFile file = getContainingFile();
    if (file instanceof PyFile) {
      for (QualifiedName name : superClasses.keySet()) {
        result.add(name != null ? classTypeFromQName(name, (PyFile)file, context) : null);
      }
    }
  }

  @Nullable
  private PyClassLikeType getImplicitSuper() {
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(this);
    final PyClassType objectType = builtinCache.getObjectType();

    if (objectType != null && this == objectType.getPyClass()) {
      return null;
    }

    if (LanguageLevel.forElement(this).isPython2() && getMetaClassQName() == null && !hasNewStyleMetaClass(this)) {
      return null;
    }

    return objectType == null ? null : objectType.toClass();
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getAncestorTypes(@NotNull final TypeEvalContext context) {
    return ContainerUtil.filter(getAncestorTypesWithMetaClassInstances(context), type ->
      type == null || type.isDefinition()
    );
  }

  @NotNull
  private List<PyClassLikeType> getAncestorTypesWithMetaClassInstances(@NotNull TypeEvalContext context) {
    return PyUtil.getParameterizedCachedValue(this, context, contextArgument -> {
      List<PyClassLikeType> ancestorTypes;
      if (isNewStyleClass(contextArgument)) {
        try {
          ancestorTypes = getMROAncestorTypes(contextArgument);
        }
        catch (final MROException ignored) {
          ancestorTypes = getOldStyleAncestorTypes(contextArgument);
          boolean hasUnresolvedAncestorTypes = false;
          for (PyClassLikeType type : ancestorTypes) {
            if (type == null) {
              hasUnresolvedAncestorTypes = true;
              break;
            }
          }
          if (!hasUnresolvedAncestorTypes) {
            ancestorTypes = Collections.singletonList(null);
          }
        }
      }
      else {
        ancestorTypes = getOldStyleAncestorTypes(contextArgument);
      }
      return ancestorTypes;
    });
  }

  @Nullable
  @Override
  public PyType getMetaClassType(@NotNull TypeEvalContext context) {
    PyPsiUtils.assertValid(this);
    if (context.maySwitchToAST(this)) {
      final PyExpression expression = getMetaClassExpression();
      if (expression != null) {
        final PyType type = context.getType(expression);
        if (type != null) {
          return type;
        }
      }
    }
    else {
      final QualifiedName name = getMetaClassQName();
      final PsiFile file = getContainingFile();
      if (file instanceof PyFile pyFile) {
        if (name != null) {
          return classTypeFromQName(name, pyFile, context);
        }
      }
    }
    final LanguageLevel level = LanguageLevel.forElement(this);
    if (level.isPython2()) {
      final PsiFile file = getContainingFile();
      if (file instanceof PyFile pyFile) {
        final PsiElement element = pyFile.getElementNamed(PyNames.DUNDER_METACLASS);
        if (element instanceof PyTypedElement) {
          return context.getType((PyTypedElement)element);
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public PyClassLikeType getMetaClassType(boolean inherited, @NotNull TypeEvalContext context) {
    if (!inherited) {
      return as(getMetaClassType(context), PyClassLikeType.class);
    }
    final List<PyClassLikeType> metaClassTypes = getAllPossibleMetaClassTypes(context);
    final PyClassLikeType mostDerivedMeta = getMostDerivedClassType(metaClassTypes, context);
    return mostDerivedMeta != null ? mostDerivedMeta : PyBuiltinCache.getInstance(this).getObjectType("type");
  }

  private static @Nullable PyClassLikeType getMostDerivedClassType(@NotNull List<@NotNull PyClassLikeType> classTypes,
                                                                   @NotNull TypeEvalContext context) {
    if (classTypes.isEmpty()) {
      return null;
    }
    try {
      return classTypes
        .stream()
        .max(
          (t1, t2) -> {
            if (Objects.equals(t1, t2)) {
              return 0;
            }
            else if (t1.getAncestorTypes(context).contains(t2)) {
              return 1;
            }
            else if (t2.getAncestorTypes(context).contains(t1)) {
              return -1;
            }
            else {
              throw new NotDerivedClassTypeException();
            }
          }
        )
        .orElse(null);
    }
    catch (NotDerivedClassTypeException ignored) {
      return null;
    }
  }

  private static final class NotDerivedClassTypeException extends RuntimeException {
  }

  private @NotNull List<@NotNull PyClassLikeType> getAllPossibleMetaClassTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> results = new ArrayList<>();
    final PyClassLikeType ownMeta = getMetaClassType(false, context);
    if (ownMeta != null) {
      results.add(ownMeta);
    }
    for (PyClassLikeType ancestor : getAncestorTypesWithMetaClassInstances(context)) {
      if (ancestor != null) {
        if (!ancestor.isDefinition()) {
          results.add(ancestor.toClass());
        }
        else {
          final PyClassLikeType ancestorMeta = ancestor.getMetaClassType(context, false);
          if (ancestorMeta != null) {
            results.add(ancestorMeta);
          }
        }
      }
    }
    return results;
  }


  @Nullable
  private QualifiedName getMetaClassQName() {
    final PyClassStub stub = getStub();
    return stub != null ? stub.getMetaClass() : PyPsiUtils.asQualifiedName(getMetaClassExpression());
  }

  @Nullable
  @Override
  public PyExpression getMetaClassExpression() {
    final LanguageLevel level = LanguageLevel.forElement(this);
    if (!level.isPython2()) {
      // Requires AST access
      for (PyExpression expression : getSuperClassExpressions()) {
        if (expression instanceof PyKeywordArgument argument) {
          if (PyNames.METACLASS.equals(argument.getKeyword())) {
            return argument.getValueExpression();
          }
        }
      }
    }
    else {
      final PyTargetExpression attribute = findClassAttribute(PyNames.DUNDER_METACLASS, false, null);
      if (attribute != null) {
        return attribute.findAssignedValue();
      }
    }

    for (PyExpression expression : getSuperClassExpressions()) {
      if (isSixWithMetaclassCall(expression)) {
        final PyExpression[] arguments = ((PyCallExpression)expression).getArguments();
        if (arguments.length != 0) {
          return arguments[0];
        }
      }
    }

    final PyDecoratorList decoratorList = getDecoratorList();
    if (decoratorList != null) {
      for (PyDecorator decorator : decoratorList.getDecorators()) {
        if (isSixAddMetaclass(decorator)) {
          final PyExpression[] arguments = decorator.getArguments();
          if (arguments.length != 0) {
            return arguments[0];
          }
        }
      }
    }

    return null;
  }

  private static boolean isSixAddMetaclass(@NotNull PyDecorator decorator) {
    final PyExpression callee = decorator.getCallee();

    if (callee instanceof PyReferenceExpression) {
      final QualifiedName sixAddMetaclass = QualifiedName.fromComponents("six", "add_metaclass");
      final QualifiedName djangoAddMetaclass = QualifiedName.fromDottedString("django.utils.six.add_metaclass");

      return ContainerUtil.exists(PyResolveUtil.resolveImportedElementQNameLocally((PyReferenceExpression)callee),
                                  name -> name.equals(sixAddMetaclass) || name.equals(djangoAddMetaclass));
    }

    return false;
  }

  @NotNull
  private List<PyClassLikeType> getMROAncestorTypes(@NotNull TypeEvalContext context) throws MROException {
    PyPsiUtils.assertValid(this);
    final PyType thisType = context.getType(this);
    if (thisType instanceof PyClassLikeType) {
      final List<PyClassLikeType> ancestorTypes = mroLinearize((PyClassLikeType)thisType, false, context, new HashMap<>());
      if (isOverriddenMRO(ancestorTypes, context)) {
        final ArrayList<PyClassLikeType> withNull = new ArrayList<>(ancestorTypes);
        withNull.add(null);
        return withNull;
      }
      return ancestorTypes;
    }
    else {
      return Collections.emptyList();
    }
  }

  private boolean isOverriddenMRO(@NotNull List<PyClassLikeType> ancestorTypes, @NotNull TypeEvalContext context) {
    final List<PyClass> classes = new ArrayList<>();
    classes.add(this);
    for (PyClassLikeType ancestorType : ancestorTypes) {
      if (ancestorType instanceof PyClassType classType) {
        classes.add(classType.getPyClass());
      }
    }

    final PyClass typeClass = PyBuiltinCache.getInstance(this).getClass("type");

    for (PyClass cls : classes) {
      final PyType metaClassType = cls.getMetaClassType(context);
      if (metaClassType instanceof PyClassType) {
        final PyClass metaClass = ((PyClassType)metaClassType).getPyClass();
        if (cls == metaClass) {
          return false;
        }
        final PyFunction mroMethod = metaClass.findMethodByName(PyNames.MRO, true, null);
        if (mroMethod != null) {
          final PyClass mroClass = mroMethod.getContainingClass();
          if (mroClass != null && mroClass != typeClass) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @NotNull
  private List<PyClassLikeType> getOldStyleAncestorTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> results = new ArrayList<>();
    final Deque<PyClassLikeType> toProcess = new LinkedList<>();
    final Set<PyClassLikeType> seen = new HashSet<>();
    final Set<PyClassLikeType> visited = new HashSet<>();
    final PyType thisType = context.getType(this);
    if (thisType instanceof PyClassLikeType) {
      toProcess.add((PyClassLikeType)thisType);
    }
    while (!toProcess.isEmpty()) {
      final PyClassLikeType currentType = toProcess.pollFirst();
      if (!visited.add(currentType)) {
        continue;
      }
      for (PyClassLikeType superType : currentType.getSuperClassTypes(context)) {
        if (superType == null || !seen.contains(superType)) {
          results.add(superType);
          seen.add(superType);
        }
        if (superType != null && !visited.contains(superType)) {
          toProcess.addLast(superType);
        }
      }
    }
    return results;
  }

  @Nullable
  private static PyClassLikeType classTypeFromQName(@NotNull QualifiedName qualifiedName, @NotNull PyFile containingFile,
                                                    @NotNull TypeEvalContext context) {
    final PsiElement element = ContainerUtil.getFirstItem(PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, containingFile, context));
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);
      if (type instanceof PyClassLikeType) {
        return (PyClassLikeType)type;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyClassLikeType getType(@NotNull TypeEvalContext context) {
    return as(context.getType(this), PyClassLikeType.class);
  }

  @Override
  @Nullable
  public PyTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(PyStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Nullable
  @Override
  public PyDecoratorList getDecoratorList() {
    return getStubOrPsiChild(PyStubElementTypes.DECORATOR_LIST);
  }

  @NotNull
  private TypeEvalContext notNullizeContext(@Nullable TypeEvalContext context) {
    return context == null ? TypeEvalContext.codeInsightFallback(getProject()) : context;
  }

  private static final class MyAttributesCollector implements Processor<PyTargetExpression> {
    private final List<PyTargetExpression> myAttributes = new ArrayList<>();

    @Override
    public boolean process(final PyTargetExpression expression) {
      myAttributes.add(expression);
      return true;
    }

    @NotNull
    List<PyTargetExpression> getAttributes() {
      return Collections.unmodifiableList(myAttributes);
    }
  }
}
