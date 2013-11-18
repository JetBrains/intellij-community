/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
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

/**
 * @author yole
 */
public class PyClassImpl extends PyPresentableElementImpl<PyClassStub> implements PyClass {
  public static final PyClass[] EMPTY_ARRAY = new PyClassImpl[0];

  private List<PyTargetExpression> myInstanceAttributes;
  private final NotNullLazyValue<CachedValue<Boolean>> myNewStyle = new NotNullLazyValue<CachedValue<Boolean>>() {
    @NotNull
    @Override
    protected CachedValue<Boolean> compute() {
      return CachedValuesManager.getManager(getProject()).createCachedValue(new NewStyleCachedValueProvider(), false);
    }
  };

  private volatile Map<String, Property> myPropertyCache;

  private class CachedAncestorsProvider implements ParameterizedCachedValueProvider<List<PyClassLikeType>, TypeEvalContext> {
    @Nullable
    @Override
    public CachedValueProvider.Result<List<PyClassLikeType>> compute(@NotNull TypeEvalContext context) {
      final List<PyClassLikeType> ancestorTypes = isNewStyleClass() ? getMROAncestorTypes(context) : getOldStyleAncestorTypes(context);
      return CachedValueProvider.Result.create(ancestorTypes, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  private final Key<ParameterizedCachedValue<List<PyClassLikeType>, TypeEvalContext>> myCachedValueKey = Key.create("cached ancestors");
  private final CachedAncestorsProvider myCachedAncestorsProvider = new CachedAncestorsProvider();

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return new PyClassTypeImpl(this, true);
  }

  private class NewStyleCachedValueProvider implements CachedValueProvider<Boolean> {
    @Override
    public Result<Boolean> compute() {
      return new Result<Boolean>(calculateNewStyleClass(), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
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
    return expression;
  }

  @NotNull
  @Override
  public List<PyClass> getAncestorClasses() {
    return getAncestorClasses(TypeEvalContext.codeInsightFallback());
  }

  @NotNull
  @Override
  public List<PyClass> getAncestorClasses(@NotNull TypeEvalContext context) {
    final List<PyClass> results = new ArrayList<PyClass>();
    for (PyClassLikeType type : getAncestorTypes(context)) {
      if (type instanceof PyClassType) {
        results.add(((PyClassType)type).getPyClass());
      }
    }
    return results;
  }

  public boolean isSubclass(PyClass parent) {
    if (this == parent) {
      return true;
    }
    for (PyClass superclass : getAncestorClasses()) {
      if (parent == superclass) return true;
    }
    return false;
  }

  @Override
  public boolean isSubclass(@NotNull String superClassQName) {
    if (superClassQName.equals(getQualifiedName())) {
      return true;
    }
    for (PyClassLikeType type : getAncestorTypes(TypeEvalContext.codeInsightFallback())) {
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
    String name = getName();
    final PyClassStub stub = getStub();
    PsiElement ancestor = stub != null ? stub.getParentStub().getPsi() : getParent();
    while (!(ancestor instanceof PsiFile)) {
      if (ancestor == null) return name;    // can this happen?
      if (ancestor instanceof PyClass) {
        name = ((PyClass)ancestor).getName() + "." + name;
      }
      ancestor = stub != null ? ((StubBasedPsiElement)ancestor).getStub().getParentStub().getPsi() : ancestor.getParent();
    }

    PsiFile psiFile = ((PsiFile)ancestor).getOriginalFile();
    final PyFile builtins = PyBuiltinCache.getInstance(this).getBuiltinsFile();
    if (!psiFile.equals(builtins)) {
      VirtualFile vFile = psiFile.getVirtualFile();
      if (vFile != null) {
        final String packageName = QualifiedNameFinder.findShortestImportableName(this, vFile);
        return packageName + "." + name;
      }
    }
    return name;
  }

  @Override
  public List<String> getSlots() {
    final Set<String> result = new LinkedHashSet<String>();
    boolean found = false;
    final List<String> ownSlots = getOwnSlots();
    if (ownSlots != null) {
      found = true;
      result.addAll(ownSlots);
    }
    for (PyClass cls : getAncestorClasses()) {
      final List<String> ancestorSlots = cls.getOwnSlots();
      if (ancestorSlots != null) {
        found = true;
        result.addAll(ancestorSlots);
      }
    }
    return found ? new ArrayList<String>(result) : null;
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
  public PyClass[] getSuperClasses() {
    final List<PyClassLikeType> superTypes = getSuperClassTypes(TypeEvalContext.codeInsightFallback());
    if (superTypes.isEmpty()) {
      return EMPTY_ARRAY;
    }
    final List<PyClass> result = new ArrayList<PyClass>();
    for (PyClassLikeType type : superTypes) {
      if (type instanceof PyClassType) {
        result.add(((PyClassType)type).getPyClass());
      }
    }
    return result.toArray(new PyClass[result.size()]);
  }

  @NotNull
  private static List<PyClassLikeType> mroMerge(@NotNull List<List<PyClassLikeType>> sequences) {
    List<PyClassLikeType> result = new LinkedList<PyClassLikeType>(); // need to insert to 0th position on linearize
    while (true) {
      // filter blank sequences
      List<List<PyClassLikeType>> nonBlankSequences = new ArrayList<List<PyClassLikeType>>(sequences.size());
      for (List<PyClassLikeType> item : sequences) {
        if (item.size() > 0) nonBlankSequences.add(item);
      }
      if (nonBlankSequences.isEmpty()) return result;
      // find a clean head
      boolean found = false;
      PyClassLikeType head = null; // to keep compiler happy; really head is assigned in the loop at least once.
      for (List<PyClassLikeType> seq : nonBlankSequences) {
        head = seq.get(0);
        boolean head_in_tails = false;
        for (List<PyClassLikeType> tail_seq : nonBlankSequences) {
          if (tail_seq.indexOf(head) > 0) { // -1 is not found, 0 is head, >0 is tail.
            head_in_tails = true;
            break;
          }
        }
        if (!head_in_tails) {
          found = true;
          break;
        }
        else {
          head = null; // as a signal
        }
      }
      if (!found) {
        // Inconsistent hierarchy results in TypeError
        throw new IllegalStateException("Inconsistent class hierarchy");
      }
      // our head is clean;
      result.add(head);
      // remove it from heads of other sequences
      for (List<PyClassLikeType> seq : nonBlankSequences) {
        if (Comparing.equal(seq.get(0), head)) {
          seq.remove(0);
        }
      }
    } // we either return inside the loop or die by assertion
  }

  @NotNull
  private static List<PyClassLikeType> mroLinearize(@NotNull PyClassLikeType type, @NotNull Set<PyClassLikeType> seen, boolean addThisType,
                                                    @NotNull TypeEvalContext context) {
    if (seen.contains(type)) {
      throw new IllegalStateException("Circular class inheritance");
    }
    final List<PyClassLikeType> bases = type.getSuperClassTypes(context);
    List<List<PyClassLikeType>> lines = new ArrayList<List<PyClassLikeType>>();
    for (PyClassLikeType base : bases) {
      if (base != null) {
        final Set<PyClassLikeType> newSeen = new HashSet<PyClassLikeType>(seen);
        newSeen.add(type);
        List<PyClassLikeType> lin = mroLinearize(base, newSeen, true, context);
        if (!lin.isEmpty()) lines.add(lin);
      }
    }
    if (!bases.isEmpty()) {
      lines.add(bases);
    }
    List<PyClassLikeType> result = mroMerge(lines);
    if (addThisType) {
      result.add(0, type);
    }
    return result;
  }

  @NotNull
  public PyFunction[] getMethods() {
    return getClassChildren(PythonDialectsTokenSetProvider.INSTANCE.getFunctionDeclarationTokens(), PyFunction.ARRAY_FACTORY);
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
    List<T> result = new ArrayList<T>();
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

  public PyFunction findMethodByName(@Nullable final String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyFunction> proc = new NameFinder<PyFunction>(name);
    visitMethods(proc, inherited);
    return proc.getResult();
  }

  @Nullable
  @Override
  public PyClass findNestedClass(String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyClass> proc = new NameFinder<PyClass>(name);
    visitNestedClasses(proc, inherited);
    return proc.getResult();
  }

  @Nullable
  public PyFunction findInitOrNew(boolean inherited) {
    NameFinder<PyFunction> proc;
    if (isNewStyleClass()) {
      proc = new NameFinder<PyFunction>(PyNames.INIT, PyNames.NEW);
    }
    else {
      proc = new NameFinder<PyFunction>(PyNames.INIT);
    }
    visitMethods(proc, inherited, true);
    return proc.getResult();
  }

  private final static Maybe<Callable> UNKNOWN_CALL = new Maybe<Callable>(); // denotes _not_ a PyFunction, actually
  private final static Maybe<Callable> NONE = new Maybe<Callable>(null); // denotes an explicit None

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
    Map<String, List<PyFunction>> grouped = new HashMap<String, List<PyFunction>>();
    // group suitable same-named methods, each group defines a property
    for (PyFunction method : getMethods()) {
      final String methodName = method.getName();
      if (name == null || name.equals(methodName)) {
        List<PyFunction> bucket = grouped.get(methodName);
        if (bucket == null) {
          bucket = new SmartList<PyFunction>();
          grouped.put(methodName, bucket);
        }
        bucket.add(method);
      }
    }
    for (Map.Entry<String, List<PyFunction>> entry : grouped.entrySet()) {
      Maybe<Callable> getter = NONE;
      Maybe<Callable> setter = NONE;
      Maybe<Callable> deleter = NONE;
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
                getter = new Maybe<Callable>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.GETTER)) {
                getter = new Maybe<Callable>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.SETTER)) {
                setter = new Maybe<Callable>(method);
              }
              else if (useAdvancedSyntax && qname.matches(decoratorName, PyNames.DELETER)) {
                deleter = new Maybe<Callable>(method);
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

  private Maybe<Callable> fromPacked(Maybe<String> maybeName) {
    if (maybeName.isDefined()) {
      final String value = maybeName.value();
      if (value == null || PyNames.NONE.equals(value)) {
        return NONE;
      }
      PyFunction method = findMethodByName(value, true);
      if (method != null) return new Maybe<Callable>(method);
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
            Maybe<Callable> getter = fromPacked(prop.getGetter());
            Maybe<Callable> setter = fromPacked(prop.getSetter());
            Maybe<Callable> deleter = fromPacked(prop.getDeleter());
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
  public Property findProperty(@NotNull final String name) {
    Property property = findLocalProperty(name);
    if (property != null) {
      return property;
    }
    if (findMethodByName(name, false) != null || findClassAttribute(name, false) != null) {
      return null;
    }
    for (PyClass aClass : getAncestorClasses()) {
      final Property ancestorProperty = ((PyClassImpl)aClass).findLocalProperty(name);
      if (ancestorProperty != null) {
        return ancestorProperty;
      }
    }
    return null;
  }

  @Override
  public Property findPropertyByCallable(Callable callable) {
    if (myPropertyCache == null) {
      myPropertyCache = initializePropertyCache();
    }
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
    if (myPropertyCache == null) {
      myPropertyCache = initializePropertyCache();
    }
    return myPropertyCache.get(name);
  }

  private Map<String, Property> initializePropertyCache() {
    final Map<String, Property> result = new HashMap<String, Property>();
    processProperties(null, new Processor<Property>() {
      @Override
      public boolean process(Property property) {
        result.put(property.getName(), property);
        return false;
      }
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
    if (!isValid()) {
      return null;
    }
    LanguageLevel level = LanguageLevel.getDefault();
    // EA-32381: A tree-based instance may not have a parent element somehow, so getContainingFile() may be not appropriate
    final PsiFile file = getParentByStub() != null ? getContainingFile() : null;
    if (file != null) {
      final VirtualFile vfile = file.getVirtualFile();
      if (vfile != null) {
        level = LanguageLevel.forFile(vfile);
      }
    }
    final boolean useAdvancedSyntax = level.isAtLeast(LanguageLevel.PYTHON26);
    final Property local = processPropertiesInClass(name, filter, useAdvancedSyntax);
    if (local != null) {
      return local;
    }
    if (inherited) {
      if (name != null && (findMethodByName(name, false) != null || findClassAttribute(name, false) != null)) {
        return null;
      }
      for (PyClass cls : getAncestorClasses()) {
        final Property property = ((PyClassImpl)cls).processPropertiesInClass(name, filter, useAdvancedSyntax);
        if (property != null) {
          return property;
        }
      }
    }
    return null;
  }

  private static class PropertyImpl extends PropertyBunch<Callable> implements Property {
    private final String myName;

    private PropertyImpl(String name,
                         Maybe<Callable> getter,
                         Maybe<Callable> setter,
                         Maybe<Callable> deleter,
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
    public Maybe<Callable> getGetter() {
      return filterNonStubExpression(myGetter);
    }

    @NotNull
    @Override
    public Maybe<Callable> getSetter() {
      return filterNonStubExpression(mySetter);
    }

    @NotNull
    @Override
    public Maybe<Callable> getDeleter() {
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
    public Maybe<Callable> getByDirection(@NotNull AccessDirection direction) {
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
      final Callable callable = myGetter.valueOrNull();
      if (callable != null) {
        // Ignore return types of non stub-based elements if we are not allowed to use AST
        if (!(callable instanceof StubBasedPsiElement) && !context.maySwitchToAST(callable)) {
          return null;
        }
        return callable.getReturnType(context, null);
      }
      return null;
    }

    @NotNull
    @Override
    protected Maybe<Callable> translate(@Nullable PyExpression expr) {
      if (expr == null) {
        return NONE;
      }
      if (PyNames.NONE.equals(expr.getName())) return NONE; // short-circuit a common case
      if (expr instanceof Callable) {
        return new Maybe<Callable>((Callable)expr);
      }
      final PsiReference ref = expr.getReference();
      if (ref != null) {
        PsiElement something = ref.resolve();
        if (something instanceof Callable) {
          return new Maybe<Callable>((Callable)something);
        }
      }
      return NONE;
    }

    @NotNull
    private static Maybe<Callable> filterNonStubExpression(@NotNull Maybe<Callable> maybeCallable) {
      final Callable callable = maybeCallable.valueOrNull();
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

  public boolean visitMethods(Processor<PyFunction> processor, boolean inherited) {
    return visitMethods(processor, inherited, false);
  }

  public boolean visitMethods(Processor<PyFunction> processor,
                              boolean inherited,
                              boolean skipClassObj) {
    PyFunction[] methods = getMethods();
    if (!ContainerUtil.process(methods, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses()) {
        if (skipClassObj && PyNames.FAKE_OLD_BASE.equals(ancestor.getName())) {
          continue;
        }
        if (!ancestor.visitMethods(processor, false)) {
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
      for (PyClass ancestor : getAncestorClasses()) {
        if (!((PyClassImpl)ancestor).visitNestedClasses(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitClassAttributes(Processor<PyTargetExpression> processor, boolean inherited) {
    List<PyTargetExpression> methods = getClassAttributes();
    if (!ContainerUtil.process(methods, processor)) return false;
    if (inherited) {
      for (PyClass ancestor : getAncestorClasses()) {
        if (!ancestor.visitClassAttributes(processor, false)) {
          return false;
        }
      }
    }
    return true;
    // NOTE: sorry, not enough metaprogramming to generalize visitMethods and visitClassAttributes
  }

  public List<PyTargetExpression> getClassAttributes() {
    PyClassStub stub = getStub();
    if (stub != null) {
      final PyTargetExpression[] children = stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
      return Arrays.asList(children);
    }
    List<PyTargetExpression> result = new ArrayList<PyTargetExpression>();
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
  public PyTargetExpression findClassAttribute(@NotNull String name, boolean inherited) {
    final NameFinder<PyTargetExpression> processor = new NameFinder<PyTargetExpression>(name);
    visitClassAttributes(processor, inherited);
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
      for (PyClass ancestor : getAncestorClasses()) {
        final PyTargetExpression attribute = ancestor.findInstanceAttribute(name, false);
        if (attribute != null) {
          return attribute;
        }
      }
    }
    return null;
  }

  private List<PyTargetExpression> collectInstanceAttributes() {
    Map<String, PyTargetExpression> result = new HashMap<String, PyTargetExpression>();

    collectAttributesInNew(result);
    PyFunctionImpl initMethod = (PyFunctionImpl)findMethodByName(PyNames.INIT, false);
    if (initMethod != null) {
      collectInstanceAttributes(initMethod, result);
    }
    Set<String> namesInInit = new HashSet<String>(result.keySet());
    final PyFunction[] methods = getMethods();
    for (PyFunction method : methods) {
      if (!PyNames.INIT.equals(method.getName())) {
        collectInstanceAttributes(method, result, namesInInit);
      }
    }

    final Collection<PyTargetExpression> expressions = result.values();
    return new ArrayList<PyTargetExpression>(expressions);
  }

  private void collectAttributesInNew(@NotNull final Map<String, PyTargetExpression> result) {
    final PyFunction newMethod = findMethodByName(PyNames.NEW, false);
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
      final List<PyTargetExpression> result = new ArrayList<PyTargetExpression>();
      if (statementList != null) {
        statementList.accept(new PyRecursiveElementVisitor() {
          @Override
          public void visitPyClass(PyClass node) {}

          public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
            for (PyExpression expression : node.getTargets()) {
              if (expression instanceof PyTargetExpression) {
                result.add((PyTargetExpression)expression);
              }
            }
          }
        });
      }
      return result;
    }
  }

  public boolean isNewStyleClass() {
    return myNewStyle.getValue().getValue();
  }

  private boolean calculateNewStyleClass() {
    final PsiFile containingFile = getContainingFile();
    if (containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isPy3K()) {
      return true;
    }
    final PyClass objClass = PyBuiltinCache.getInstance(this).getClass("object");
    if (this == objClass) return true; // a rare but possible case
    if (hasNewStyleMetaClass(this)) return true;
    for (PyClassLikeType type : getOldStyleAncestorTypes(TypeEvalContext.codeInsightFallback())) {
      if (type == null) {
        // unknown, assume new-style class
        return true;
      }
      if (type instanceof PyClassType) {
        final PyClass pyClass = ((PyClassType)type).getPyClass();
        if (pyClass == objClass) return true;
        if (hasNewStyleMetaClass(pyClass)) {
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
    if (pyClass.findClassAttribute(PyNames.DUNDER_METACLASS, false) != null) {
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
    Map<String, PyTargetExpression> declarationsInMethod = new HashMap<String, PyTargetExpression>();
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

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return Collections.<PyElement>singleton(this);
  }

  public PyElement getElementNamed(final String the_name) {
    return the_name.equals(getName()) ? this : null;
  }

  public boolean mustResolveOutside() {
    return false;
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
  public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    if (PyNames.FAKE_OLD_BASE.equals(getName())) {
      return Collections.emptyList();
    }
    final PyClassStub stub = getStub();
    final List<PyClassLikeType> result = new ArrayList<PyClassLikeType>();
    if (stub != null) {
      final PsiElement parent = stub.getParentStub().getPsi();
      if (parent instanceof PyFile) {
        final PyFile file = (PyFile)parent;
        for (QualifiedName name : stub.getSuperClasses()) {
          result.add(name != null ? classTypeFromQName(name, file, context) : null);
        }
      }
    }
    else {
      for (PyExpression expression : getSuperClassExpressions()) {
        expression = unfoldClass(expression);
        if (expression instanceof PyKeywordArgument) {
          continue;
        }
        final PyType type = context.getType(expression);
        result.add(type instanceof PyClassLikeType ? (PyClassLikeType)type : null);
      }
    }
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(this);
    if (result.isEmpty() && isValid() && !builtinCache.hasInBuiltins(this)) {
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

  @NotNull
  @Override
  public List<PyClassLikeType> getAncestorTypes(@NotNull TypeEvalContext context) {
    // TODO: Return different cached copies depending on the type eval context parameters
    final CachedValuesManager manager = CachedValuesManager.getManager(getProject());
    return manager.getParameterizedCachedValue(this, myCachedValueKey, myCachedAncestorsProvider, false, context);
  }

  @NotNull
  private List<PyClassLikeType> getMROAncestorTypes(@NotNull TypeEvalContext context) {
    final PyType thisType = context.getType(this);
    if (thisType instanceof PyClassLikeType) {
      try {
        return mroLinearize((PyClassLikeType)thisType, new HashSet<PyClassLikeType>(), false, context);
      }
      catch (IllegalStateException ignored) {
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private List<PyClassLikeType> getOldStyleAncestorTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> results = new ArrayList<PyClassLikeType>();
    final List<PyClassLikeType> toProcess = new ArrayList<PyClassLikeType>();
    final Set<PyClassLikeType> seen = new HashSet<PyClassLikeType>();
    final Set<PyClassLikeType> visited = new HashSet<PyClassLikeType>();
    final PyType thisType = context.getType(this);
    if (thisType instanceof PyClassLikeType) {
      toProcess.add((PyClassLikeType)thisType);
    }
    while (!toProcess.isEmpty()) {
      final PyClassLikeType currentType = toProcess.remove(0);
      visited.add(currentType);
      for (PyClassLikeType superType : currentType.getSuperClassTypes(context)) {
        if (superType == null || !seen.contains(superType)) {
          results.add(superType);
          seen.add(superType);
        }
        if (superType != null && !visited.contains(superType)) {
          toProcess.add(superType);
        }
      }
    }
    return results;
  }

  @Nullable
  private static PsiElement getElementQNamed(@NotNull NameDefiner nameDefiner, @NotNull QualifiedName qualifiedName) {
    final int componentCount = qualifiedName.getComponentCount();
    final String fullName = qualifiedName.toString();
    if (componentCount == 0) {
      return null;
    }
    else if (componentCount == 1) {
      PsiElement element = nameDefiner.getElementNamed(fullName);
      if (element == null) {
        element = PyBuiltinCache.getInstance(nameDefiner).getByName(fullName);
      }
      return element;
    }
    else {
      final String name = qualifiedName.getLastComponent();
      final QualifiedName containingQName = qualifiedName.removeLastComponent();
      NameDefiner definer = nameDefiner;
      for (String component : containingQName.getComponents()) {
        PsiElement element = PyUtil.turnDirIntoInit(definer.getElementNamed(component));
        if (element instanceof PyImportElement) {
          element = ((PyImportElement)element).resolve();
        }
        if (element instanceof NameDefiner) {
          definer = (NameDefiner)element;
        }
        else {
          definer = null;
          break;
        }
      }
      if (definer != null) {
        return definer.getElementNamed(name);
      }
      return null;
    }
  }

  @Nullable
  private static PyClassLikeType classTypeFromQName(@NotNull QualifiedName qualifiedName, @NotNull PyFile containingFile,
                                                    @NotNull TypeEvalContext context) {
    final PsiElement element = getElementQNamed(containingFile, qualifiedName);
    if (element instanceof PyTypedElement) {
      final PyType type = context.getType((PyTypedElement)element);
      if (type instanceof PyClassLikeType) {
        return (PyClassLikeType)type;
      }
    }
    return null;
  }
}
