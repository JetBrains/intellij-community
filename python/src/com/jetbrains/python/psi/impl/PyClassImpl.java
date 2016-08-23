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
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
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
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.stubs.PyClassElementType;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.notNullize;

/**
 * @author yole
 */
public class PyClassImpl extends PyBaseElementImpl<PyClassStub> implements PyClass {
  public static class MROException extends Exception {
    public MROException(String s) {
      super(s);
    }
  }

  public static final PyClass[] EMPTY_ARRAY = new PyClassImpl[0];

  /**
   * Ancestors cache is lazy because provider ({@link PyClassImpl.CachedAncestorsProvider}) needs
   * {@link #getProject()} which is not available during constructor time.
   */
  private TypeEvalContextBasedCache<List<PyClassLikeType>> myAncestorsCache;

  /**
   * Lock to create {@link #myAncestorsCache} in lazy way.
   */
  @NotNull
  private final Object myAncestorsCacheLock = new Object();

  /**
   * Engine to create list of ancestors based on context
   */
  private class CachedAncestorsProvider implements Function<TypeEvalContext, List<PyClassLikeType>> {
    @Nullable
    @Override
    public List<PyClassLikeType> fun(@NotNull TypeEvalContext context) {
      List<PyClassLikeType> ancestorTypes;
      if (isNewStyleClass(context)) {
        try {
          ancestorTypes = getMROAncestorTypes(context);
        }
        catch (MROException e) {
          ancestorTypes = getOldStyleAncestorTypes(context);
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
        ancestorTypes = getOldStyleAncestorTypes(context);
      }
      return ancestorTypes;
    }
  }

  private List<PyTargetExpression> myInstanceAttributes;

  private volatile Map<String, Property> myPropertyCache;

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return new PyClassTypeImpl(this, true);
  }

  private class NewStyleCachedValueProvider implements ParameterizedCachedValueProvider<Boolean, TypeEvalContext> {

    @Nullable
    @Override
    public CachedValueProvider.Result<Boolean> compute(TypeEvalContext param) {
      return new CachedValueProvider.Result<>(calculateNewStyleClass(param), PsiModificationTracker.MODIFICATION_COUNT);
    }
  }

  public PyClassImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  public PyClassImpl(@NotNull final PyClassStub stub) {
    this(stub, PyElementTypes.CLASS_DECLARATION);
  }

  public PyClassImpl(@NotNull final PyClassStub stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  /**
   * @return ancestors cache. It is created lazily if needed.
   */
  @NotNull
  private TypeEvalContextBasedCache<List<PyClassLikeType>> getAncestorsCache() {
    if (myAncestorsCache != null) {
      return myAncestorsCache;
    }
    synchronized (myAncestorsCacheLock) {
      if (myAncestorsCache == null) {
        myAncestorsCache = new TypeEvalContextBasedCache<>(
          CachedValuesManager.getManager(getProject()),
          new CachedAncestorsProvider()
        );
      }
      return myAncestorsCache;
    }
  }

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
      ASTNode node = getNameNode();
      return node != null ? node.getText() : null;
    }
  }

  public PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.CLASS_ICON;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }

  @Override
  @NotNull
  public PyStatementList getStatementList() {
    final PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for class " + getText();
    return statementList;
  }

  @Override
  public PyArgumentList getSuperClassExpressionList() {
    final PyArgumentList argList = PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
    if (argList != null && argList.getFirstChild() != null) {
      return argList;
    }
    return null;
  }

  @Override
  @NotNull
  public PyExpression[] getSuperClassExpressions() {
    final PyArgumentList argList = getSuperClassExpressionList();
    if (argList != null) {
      return argList.getArguments();
    }
    return PyExpression.EMPTY_ARRAY;
  }

  @NotNull
  public static PyExpression unfoldClass(@NotNull PyExpression expression) {
    if (expression instanceof PyCallExpression) {
      PyCallExpression call = (PyCallExpression)expression;
      final PyExpression callee = call.getCallee();
      final PyExpression[] arguments = call.getArguments();
      if (callee != null && "with_metaclass".equals(callee.getName()) && arguments.length > 1) {
        final PyExpression secondArgument = arguments[1];
        if (secondArgument != null) {
          return secondArgument;
        }
      }
    }
    // Heuristic: unfold Foo[Bar] to Foo for subscription expressions for superclasses
    else if (expression instanceof PySubscriptionExpression) {
      final PySubscriptionExpression subscriptionExpr = (PySubscriptionExpression)expression;
      return subscriptionExpr.getOperand();
    }
    return expression;
  }

  @NotNull
  @Override
  public final List<PyClass> getAncestorClasses(@Nullable final TypeEvalContext context) {
    final List<PyClass> results = new ArrayList<>();
    final TypeEvalContext contextToUse = (context != null ? context : TypeEvalContext.codeInsightFallback(getProject()));
    for (final PyClassLikeType type : getAncestorTypes(contextToUse)) {
      if (type instanceof PyClassType) {
        results.add(((PyClassType)type).getPyClass());
      }
    }
    return results;
  }

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
    if (context == null) {
      context = TypeEvalContext.codeInsightFallback(getProject());
    }
    if (superClassQName.equals(getQualifiedName())) {
      return true;
    }
    for (PyClassLikeType type : getAncestorTypes(context)) {
      if (type != null && superClassQName.equals(type.getClassQName())) {
        return true;
      }
    }
    return false;
  }

  public PyDecoratorList getDecoratorList() {
    return getStubOrPsiChild(PyElementTypes.DECORATOR_LIST);
  }

  @Nullable
  public String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }

  @Override
  public List<String> getSlots(TypeEvalContext context) {
    final Set<String> result = new LinkedHashSet<>();
    boolean found = false;
    final List<String> ownSlots = getOwnSlots();
    if (ownSlots != null) {
      found = true;
      result.addAll(ownSlots);
    }
    for (PyClass cls : getAncestorClasses(context)) {
      final List<String> ancestorSlots = cls.getOwnSlots();
      if (ancestorSlots != null) {
        found = true;
        result.addAll(ancestorSlots);
      }
    }
    return found ? new ArrayList<>(result) : null;
  }

  @Nullable
  @Override
  public List<String> getOwnSlots() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getSlots();
    }
    return PyFileImpl.getStringListFromTargetExpression(PyNames.SLOTS, getClassAttributes());
  }

  @NotNull
  public PyClass[] getSuperClasses(@Nullable TypeEvalContext context) {
    if (context == null) {
      context = TypeEvalContext.codeInsightFallback(getProject());
    }
    final List<PyClassLikeType> superTypes = getSuperClassTypes(context);
    if (superTypes.isEmpty()) {
      return EMPTY_ARRAY;
    }
    final List<PyClass> result = new ArrayList<>();
    for (PyClassLikeType type : superTypes) {
      if (type instanceof PyClassType) {
        result.add(((PyClassType)type).getPyClass());
      }
    }
    return result.toArray(new PyClass[result.size()]);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this) {
      @Nullable
      @Override
      public String getPresentableText() {
        PyPsiUtils.assertValid(PyClassImpl.this);
        final StringBuilder result = new StringBuilder(notNullize(getName(), PyNames.UNNAMED_ELEMENT));
        final PyExpression[] superClassExpressions = getSuperClassExpressions();
        if (superClassExpressions.length > 0) {
          result.append("(");
          result.append(join(Arrays.asList(superClassExpressions), expr -> {
            String name = expr.getText();
            return notNullize(name, PyNames.UNNAMED_ELEMENT);
          }, ", "));
          result.append(")");
        }
        return result.toString();
      }
    };
  }

  @NotNull
  private static List<PyClassLikeType> mroMerge(@NotNull List<List<PyClassLikeType>> sequences) throws MROException {
    List<PyClassLikeType> result = new LinkedList<>(); // need to insert to 0th position on linearize
    while (true) {
      // filter blank sequences
      final List<List<PyClassLikeType>> nonBlankSequences = new ArrayList<>(sequences.size());
      for (List<PyClassLikeType> item : sequences) {
        if (item.size() > 0) nonBlankSequences.add(item);
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
          final List<PyClassLikeType> baseClassMRO = mroLinearize(base, true, context, cache);
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
  @NotNull
  public PyFunction[] getMethods() {
    return getClassChildren(PythonDialectsTokenSetProvider.INSTANCE.getFunctionDeclarationTokens(), PyFunction.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public Map<String, Property> getProperties() {
    initProperties();
    return new HashMap<>(myPropertyCache);
  }

  @Override
  public PyClass[] getNestedClasses() {
    return getClassChildren(TokenSet.create(PyElementTypes.CLASS_DECLARATION), PyClass.ARRAY_FACTORY);
  }

  protected <T extends PsiElement> T[] getClassChildren(TokenSet elementTypes, ArrayFactory<T> factory) {
    // TODO: gather all top-level functions, maybe within control statements
    final PyClassStub classStub = getStub();
    if (classStub != null) {
      return classStub.getChildrenByType(elementTypes, factory);
    }
    List<T> result = new ArrayList<>();
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (elementTypes.contains(element.getNode().getElementType())) {
        //noinspection unchecked
        result.add((T)element);
      }
    }
    return result.toArray(factory.create(result.size()));
  }

  private static class NameFinder<T extends PyElement> implements Processor<T> {
    private T myResult;
    private final String[] myNames;

    public NameFinder(String... names) {
      myNames = names;
      myResult = null;
    }

    public T getResult() {
      return myResult;
    }

    public boolean process(T target) {
      final String targetName = target.getName();
      for (String name : myNames) {
        if (name.equals(targetName)) {
          myResult = target;
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public PyFunction findMethodByName(@Nullable final String name, boolean inherited, @Nullable TypeEvalContext context) {
    if (name == null) return null;
    NameFinder<PyFunction> proc = new NameFinder<>(name);
    visitMethods(proc, inherited, context);
    return proc.getResult();
  }

  @Nullable
  @Override
  public PyClass findNestedClass(String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyClass> proc = new NameFinder<>(name);
    visitNestedClasses(proc, inherited);
    return proc.getResult();
  }

  @Nullable
  public PyFunction findInitOrNew(boolean inherited, final @Nullable TypeEvalContext context) {
    NameFinder<PyFunction> proc;
    if (isNewStyleClass(context)) {
      proc = new NameFinder<>(PyNames.INIT, PyNames.NEW);
    }
    else {
      proc = new NameFinder<>(PyNames.INIT);
    }
    visitMethods(proc, inherited, true, context);
    return proc.getResult();
  }

  private final static Maybe<PyCallable> UNKNOWN_CALL = new Maybe<>(); // denotes _not_ a PyFunction, actually
  private final static Maybe<PyCallable> NONE = new Maybe<>(null); // denotes an explicit None

  /**
   * @param name            name of the property
   * @param property_filter returns true if the property is acceptable
   * @param advanced        is @foo.setter syntax allowed
   * @return the first property that both filters accepted.
   */
  @Nullable
  private Property processPropertiesInClass(@Nullable String name, @Nullable Processor<Property> property_filter, boolean advanced) {
    // NOTE: fast enough to be rerun every time
    Property prop = processDecoratedProperties(name, property_filter, advanced);
    if (prop != null) return prop;
    if (getStub() != null) {
      prop = processStubProperties(name, property_filter);
      if (prop != null) return prop;
    }
    else {
      // name = property(...) assignments from PSI
      for (PyTargetExpression target : getClassAttributes()) {
        if (name == null || name.equals(target.getName())) {
          prop = PropertyImpl.fromTarget(target);
          if (prop != null) {
            if (property_filter == null || property_filter.process(prop)) return prop;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private Property processDecoratedProperties(@Nullable String name, @Nullable Processor<Property> filter, boolean useAdvancedSyntax) {
    // look at @property decorators
    Map<String, List<PyFunction>> grouped = new HashMap<>();
    // group suitable same-named methods, each group defines a property
    for (PyFunction method : getMethods()) {
      final String methodName = method.getName();
      if (name == null || name.equals(methodName)) {
        List<PyFunction> bucket = grouped.get(methodName);
        if (bucket == null) {
          bucket = new SmartList<>();
          grouped.put(methodName, bucket);
        }
        bucket.add(method);
      }
    }
    for (Map.Entry<String, List<PyFunction>> entry : grouped.entrySet()) {
      Maybe<PyCallable> getter = NONE;
      Maybe<PyCallable> setter = NONE;
      Maybe<PyCallable> deleter = NONE;
      String doc = null;
      final String decoratorName = entry.getKey();
      for (PyFunction method : entry.getValue()) {
        final PyDecoratorList decoratorList = method.getDecoratorList();
        if (decoratorList != null) {
          for (PyDecorator deco : decoratorList.getDecorators()) {
            final QualifiedName qname = deco.getQualifiedName();
            if (qname != null) {
              String decoName = qname.toString();
              for (PyKnownDecoratorProvider provider : PyUtil.KnownDecoratorProviderHolder.KNOWN_DECORATOR_PROVIDERS) {
                final String knownName = provider.toKnownDecorator(decoName);
                if (knownName != null) {
                  decoName = knownName;
                }
              }
              if (PyNames.PROPERTY.equals(decoName)) {
                getter = new Maybe<>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.GETTER)) {
                getter = new Maybe<>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.SETTER)) {
                setter = new Maybe<>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.DELETER)) {
                deleter = new Maybe<>(method);
              }
            }
          }
        }
        if (getter != NONE && setter != NONE && deleter != NONE) break; // can't improve
      }
      if (getter != NONE || setter != NONE || deleter != NONE) {
        final PropertyImpl prop = new PropertyImpl(decoratorName, getter, setter, deleter, doc, null);
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
  private Property processStubProperties(@Nullable String name, @Nullable Processor<Property> propertyProcessor) {
    final PyClassStub stub = getStub();
    if (stub != null) {
      for (StubElement subStub : stub.getChildrenStubs()) {
        if (subStub.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
          final PyTargetExpressionStub targetStub = (PyTargetExpressionStub)subStub;
          PropertyStubStorage prop = targetStub.getCustomStub(PropertyStubStorage.class);
          if (prop != null && (name == null || name.equals(targetStub.getName()))) {
            Maybe<PyCallable> getter = fromPacked(prop.getGetter());
            Maybe<PyCallable> setter = fromPacked(prop.getSetter());
            Maybe<PyCallable> deleter = fromPacked(prop.getDeleter());
            String doc = prop.getDoc();
            if (getter != NONE || setter != NONE || deleter != NONE) {
              final PropertyImpl property = new PropertyImpl(targetStub.getName(), getter, setter, deleter, doc, targetStub.getPsi());
              if (propertyProcessor == null || propertyProcessor.process(property)) return property;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Property findProperty(@NotNull final String name, boolean inherited, @Nullable final TypeEvalContext context) {
    Property property = findLocalProperty(name);
    if (property != null) {
      return property;
    }
    if (findMethodByName(name, false, null) != null || findClassAttribute(name, false, null) != null) {
      return null;
    }
    if (inherited) {
      for (PyClass aClass : getAncestorClasses(context)) {
        final Property ancestorProperty = ((PyClassImpl)aClass).findLocalProperty(name);
        if (ancestorProperty != null) {
          return ancestorProperty;
        }
      }
    }
    return null;
  }

  @Override
  public Property findPropertyByCallable(PyCallable callable) {
    initProperties();
    for (Property property : myPropertyCache.values()) {
      if (property.getGetter().valueOrNull() == callable ||
          property.getSetter().valueOrNull() == callable ||
          property.getDeleter().valueOrNull() == callable) {
        return property;
      }
    }
    return null;
  }

  private Property findLocalProperty(String name) {
    initProperties();
    return myPropertyCache.get(name);
  }

  private synchronized void initProperties() {
    if (myPropertyCache == null) {
      myPropertyCache = initializePropertyCache();
    }
  }

  private Map<String, Property> initializePropertyCache() {
    final Map<String, Property> result = new HashMap<>();
    processProperties(null, property -> {
      result.put(property.getName(), property);
      return false;
    }, false);
    return result;
  }

  @Nullable
  @Override
  public Property scanProperties(@Nullable Processor<Property> filter, boolean inherited) {
    return processProperties(null, filter, inherited);
  }

  @Nullable
  private Property processProperties(@Nullable String name, @Nullable Processor<Property> filter, boolean inherited) {
    PyPsiUtils.assertValid(this);
    LanguageLevel level = LanguageLevel.getDefault();
    // EA-32381: A tree-based instance may not have a parent element somehow, so getContainingFile() may be not appropriate
    final PsiFile file = getParentByStub() != null ? getContainingFile() : null;
    if (file != null) {
      level = LanguageLevel.forElement(file);
    }
    final boolean useAdvancedSyntax = level.isAtLeast(LanguageLevel.PYTHON26);
    final Property local = processPropertiesInClass(name, filter, useAdvancedSyntax);
    if (local != null) {
      return local;
    }
    if (inherited) {
      if (name != null && (findMethodByName(name, false, null) != null || findClassAttribute(name, false, null) != null)) {
        return null;
      }
      for (PyClass cls : getAncestorClasses(null)) {
        final Property property = ((PyClassImpl)cls).processPropertiesInClass(name, filter, useAdvancedSyntax);
        if (property != null) {
          return property;
        }
      }
    }
    return null;
  }

  private static class PropertyImpl extends PropertyBunch<PyCallable> implements Property {
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

    public String getName() {
      return myName;
    }

    public PyTargetExpression getDefinitionSite() {
      return mySite;
    }

    @NotNull
    @Override
    public Maybe<PyCallable> getByDirection(@NotNull AccessDirection direction) {
      switch (direction) {
        case READ:
          return getGetter();
        case WRITE:
          return getSetter();
        case DELETE:
          return getDeleter();
      }
      throw new IllegalArgumentException("Unknown direction " + PyUtil.nvl(direction));
    }

    @Nullable
    @Override
    public PyType getType(@NotNull TypeEvalContext context) {
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
        return context.getReturnType(callable);
      }
      return null;
    }

    @NotNull
    @Override
    protected Maybe<PyCallable> translate(@Nullable PyExpression expr) {
      if (expr == null) {
        return NONE;
      }
      if (PyNames.NONE.equals(expr.getName())) return NONE; // short-circuit a common case
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

  public boolean visitMethods(Processor<PyFunction> processor, boolean inherited, @Nullable final TypeEvalContext context) {
    return visitMethods(processor, inherited, false, context);
  }

  private boolean visitMethods(Processor<PyFunction> processor,
                              boolean inherited,
                              boolean skipClassObj, TypeEvalContext context) {
    PyFunction[] methods = getMethods();
    if (!ContainerUtil.process(methods, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses(context)) {
        if (skipClassObj && PyNames.FAKE_OLD_BASE.equals(ancestor.getName())) {
          continue;
        }
        if (!ancestor.visitMethods(processor, false, null)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitNestedClasses(Processor<PyClass> processor, boolean inherited) {
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

  public boolean visitClassAttributes(Processor<PyTargetExpression> processor, boolean inherited, @Nullable final TypeEvalContext context) {
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

  public List<PyTargetExpression> getClassAttributes() {
    PyClassStub stub = getStub();
    if (stub != null) {
      final PyTargetExpression[] children = stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
      return Arrays.asList(children);
    }
    List<PyTargetExpression> result = new ArrayList<>();
    for (PsiElement psiElement : getStatementList().getChildren()) {
      if (psiElement instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)psiElement;
        final PyExpression[] targets = assignmentStatement.getTargets();
        for (PyExpression target : targets) {
          if (target instanceof PyTargetExpression) {
            result.add((PyTargetExpression)target);
          }
        }
      }
    }
    return result;
  }

  @Override
  public PyTargetExpression findClassAttribute(@NotNull String name, boolean inherited, TypeEvalContext context) {
    final NameFinder<PyTargetExpression> processor = new NameFinder<>(name);
    visitClassAttributes(processor, inherited, context);
    return processor.getResult();
  }

  public List<PyTargetExpression> getInstanceAttributes() {
    if (myInstanceAttributes == null) {
      myInstanceAttributes = collectInstanceAttributes();
    }
    return myInstanceAttributes;
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

  private List<PyTargetExpression> collectInstanceAttributes() {
    Map<String, PyTargetExpression> result = new HashMap<>();

    collectAttributesInNew(result);
    PyFunctionImpl initMethod = (PyFunctionImpl)findMethodByName(PyNames.INIT, false, null);
    if (initMethod != null) {
      collectInstanceAttributes(initMethod, result);
    }
    Set<String> namesInInit = new HashSet<>(result.keySet());
    final PyFunction[] methods = getMethods();
    for (PyFunction method : methods) {
      if (!PyNames.INIT.equals(method.getName())) {
        collectInstanceAttributes(method, result, namesInInit);
      }
    }

    final Collection<PyTargetExpression> expressions = result.values();
    return new ArrayList<>(expressions);
  }

  private void collectAttributesInNew(@NotNull final Map<String, PyTargetExpression> result) {
    final PyFunction newMethod = findMethodByName(PyNames.NEW, false, null);
    if (newMethod != null) {
      for (PyTargetExpression target : getTargetExpressions(newMethod)) {
        result.put(target.getName(), target);
      }
    }
  }

  public static void collectInstanceAttributes(@NotNull PyFunction method, @NotNull final Map<String, PyTargetExpression> result) {
    collectInstanceAttributes(method, result, null);
  }

  public static void collectInstanceAttributes(@NotNull PyFunction method,
                                               @NotNull final Map<String, PyTargetExpression> result,
                                               Set<String> existing) {
    final PyParameter[] params = method.getParameterList().getParameters();
    if (params.length == 0) {
      return;
    }
    for (PyTargetExpression target : getTargetExpressions(method)) {
      if (PyUtil.isInstanceAttribute(target) && (existing == null || !existing.contains(target.getName()))) {
        result.put(target.getName(), target);
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
        public void visitPyClass(PyClass node) {
        }

        public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
          for (PyExpression expression : node.getTargets()) {
            if (expression instanceof PyTargetExpression) {
              result.add((PyTargetExpression)expression);
            }
          }
        }
      });
      return result;
    }
  }

  public boolean isNewStyleClass(@Nullable TypeEvalContext context) {
    return new NotNullLazyValue<ParameterizedCachedValue<Boolean, TypeEvalContext>>() {
      @NotNull
      @Override
      protected ParameterizedCachedValue<Boolean, TypeEvalContext> compute() {
        return CachedValuesManager.getManager(getProject()).createParameterizedCachedValue(new NewStyleCachedValueProvider(), false);
      }
    }.getValue().getValue(context);
  }

  private boolean calculateNewStyleClass(@Nullable TypeEvalContext context) {
    final PsiFile containingFile = getContainingFile();
    if (containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isPy3K()) {
      return true;
    }
    final PyClass objClass = PyBuiltinCache.getInstance(this).getClass(PyNames.OBJECT);
    if (this == objClass) return true; // a rare but possible case
    if (hasNewStyleMetaClass(this)) return true;
    TypeEvalContext contextToUse = (context != null ? context : TypeEvalContext.codeInsightFallback(getProject()));
    for (PyClassLikeType type : getOldStyleAncestorTypes(contextToUse)) {
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
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
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
  public boolean processInstanceLevelDeclarations(@NotNull PsiScopeProcessor processor, @Nullable PsiElement location) {
    Map<String, PyTargetExpression> declarationsInMethod = new HashMap<>();
    PyFunction instanceMethod = PsiTreeUtil.getParentOfType(location, PyFunction.class);
    final PyClass containingClass = instanceMethod != null ? instanceMethod.getContainingClass() : null;
    if (instanceMethod != null && containingClass != null && CompletionUtil.getOriginalElement(containingClass) == this) {
      collectInstanceAttributes(instanceMethod, declarationsInMethod);
      for (PyTargetExpression targetExpression : declarationsInMethod.values()) {
        if (!processor.execute(targetExpression, ResolveState.initial())) {
          return false;
        }
      }
    }
    for (PyTargetExpression expr : getInstanceAttributes()) {
      if (declarationsInMethod.containsKey(expr.getName())) {
        continue;
      }
      if (!processor.execute(expr, ResolveState.initial())) return false;
    }
    return true;
  }

  public int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public PyStringLiteralExpression getDocStringExpression() {
    return DocStringUtil.findDocStringExpression(getStatementList());
  }

  @Override
  public String getDocStringValue() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return DocStringUtil.getDocStringValue(this);
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return DocStringUtil.getStructuredDocString(this);
  }

  public String toString() {
    return "PyClass: " + getName();
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
    if (myInstanceAttributes != null) {
      myInstanceAttributes = null;
    }
    myPropertyCache = null;
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
    if (PyNames.FAKE_OLD_BASE.equals(getName())) {
      return Collections.emptyList();
    }
    final PyClassStub stub = getStub();
    final List<PyClassLikeType> result = new ArrayList<>();

    // In some cases stub may not provide all information, so we use stubs only if AST access id disabled
    if (!context.maySwitchToAST(this)) {
      fillSuperClassesNoSwitchToAst(context, stub, result);
    }
    else {
      fillSuperClassesSwitchingToAst(context, result);
    }

    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(this);
    PyPsiUtils.assertValid(this);
    if (result.isEmpty() && isValid() && !builtinCache.isBuiltin(this)) {
      final String implicitSuperName = LanguageLevel.forElement(this).isPy3K() ? PyNames.OBJECT : PyNames.FAKE_OLD_BASE;
      final PyClass implicitSuper = builtinCache.getClass(implicitSuperName);
      if (implicitSuper != null) {
        final PyType type = context.getType(implicitSuper);
        if (type instanceof PyClassLikeType) {
          result.add((PyClassLikeType)type);
        }
      }
    }
    return result;
  }

  private void fillSuperClassesSwitchingToAst(@NotNull TypeEvalContext context, List<PyClassLikeType> result) {
    for (PyExpression expression : getSuperClassExpressions()) {
      context.getType(expression);
      expression = unfoldClass(expression);
      if (expression instanceof PyKeywordArgument) {
        continue;
      }
      final PyType type = context.getType(expression);
      PyClassLikeType classLikeType = null;
      if (type instanceof PyClassLikeType) {
        classLikeType = (PyClassLikeType)type;
      }
      else {
        final PsiReference ref = expression.getReference();
        if (ref != null) {
          final PsiElement resolved = ref.resolve();
          if (resolved instanceof PyClass) {
            final PyType resolvedType = context.getType((PyClass)resolved);
            if (resolvedType instanceof PyClassLikeType) {
              classLikeType = (PyClassLikeType)resolvedType;
            }
         }
        }
      }
      result.add(classLikeType);
    }
  }

  private void fillSuperClassesNoSwitchToAst(@NotNull final TypeEvalContext context,
                                             @Nullable final PyClassStub stub,
                                             @NotNull final List<PyClassLikeType> result) {
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

  @NotNull
  @Override
  public List<PyClassLikeType> getAncestorTypes(@NotNull TypeEvalContext context) {
    return getAncestorsCache().getValue(context);
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
      final PyClassStub stub = getStub();
      final QualifiedName name = stub != null ? stub.getMetaClass() : PyPsiUtils.asQualifiedName(getMetaClassExpression());
      final PsiFile file = getContainingFile();
      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        if (name != null) {
          return classTypeFromQName(name, pyFile, context);
        }
      }
    }
    final LanguageLevel level = LanguageLevel.forElement(this);
    if (level.isOlderThan(LanguageLevel.PYTHON30)) {
      final PsiFile file = getContainingFile();
      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        final PsiElement element = pyFile.getElementNamed(PyNames.DUNDER_METACLASS);
        if (element instanceof PyTypedElement) {
          return context.getType((PyTypedElement)element);
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyExpression getMetaClassExpression() {
    final LanguageLevel level = LanguageLevel.forElement(this);
    if (level.isAtLeast(LanguageLevel.PYTHON30)) {
      // Requires AST access
      for (PyExpression expression : getSuperClassExpressions()) {
        if (expression instanceof PyKeywordArgument) {
          final PyKeywordArgument argument = (PyKeywordArgument)expression;
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
    return null;
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
      if (ancestorType instanceof PyClassType) {
        final PyClassType classType = (PyClassType)ancestorType;
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
  private static PsiElement getElementQNamed(@NotNull PyFile file, @NotNull QualifiedName qualifiedName, @NotNull TypeEvalContext context) {
    final int componentCount = qualifiedName.getComponentCount();
    final String fullName = qualifiedName.toString();
    final PyType type = new PyModuleType(file);
    if (componentCount == 0) {
      return null;
    }
    else if (componentCount == 1) {
      PsiElement element = resolveTypeMember(type, fullName, context);
      if (element == null) {
        element = PyBuiltinCache.getInstance(file).getByName(fullName);
      }
      return element;
    }
    else {
      final String name = qualifiedName.getLastComponent();
      final QualifiedName containingQName = qualifiedName.removeLastComponent();
      PyType currentType = type;
      for (String component : containingQName.getComponents()) {
        currentType = getMemberType(currentType, component, context);
        if (currentType == null) {
          return null;
        }
      }
      if (name != null) {
        return resolveTypeMember(currentType, name, context);
      }
      return null;
    }
  }

  @Nullable
  private static PyType getMemberType(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyType result;
    PsiElement element = resolveTypeMember(type, name, context);
    if (element instanceof PyImportedModule) {
      result = new PyImportedModuleType((PyImportedModule)element);
    }
    else if (element instanceof PyTypedElement) {
      result = context.getType((PyTypedElement)element);
    }
    else {
      return null;
    }
    if (result instanceof PyClassLikeType) {
      return ((PyClassLikeType)result).toInstance();
    }
    return result;
  }

  @Nullable
  private static PsiElement resolveTypeMember(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
    return (results != null && !results.isEmpty()) ? results.get(0).getElement() : null;
  }

  @Nullable
  private static PyClassLikeType classTypeFromQName(@NotNull QualifiedName qualifiedName, @NotNull PyFile containingFile,
                                                    @NotNull TypeEvalContext context) {
    final PsiElement element = getElementQNamed(containingFile, qualifiedName, context);
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
    return PyUtil.as(context.getType(this), PyClassLikeType.class);
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
