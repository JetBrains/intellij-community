package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
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

  private class NewStyleCachedValueProvider implements CachedValueProvider<Boolean> {
    @Override
    public Result<Boolean> compute() {
      return new Result<Boolean>(calculateNewStyleClass(), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  public PyClassImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyClassImpl(final PyClassStub stub) {
    super(stub, PyElementTypes.CLASS_DECLARATION);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = PyElementGenerator.getInstance(getProject()).createNameIdentifier(name);
    getNode().replaceChild(getNameNode(), nameElement);
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
    assert statementList != null: "Statement list missing for class " + getText();
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
  public PsiElement[] getSuperClassElements() {
    final PyExpression[] superExpressions = getSuperClassExpressions();
    List<PsiElement> superClasses = new ArrayList<PsiElement>();
    for(PyExpression expr: superExpressions) {
      if (expr instanceof PyReferenceExpression) {
        PyReferenceExpression ref = (PyReferenceExpression) expr;
        final PsiElement result = ref.getReference(PyResolveContext.noProperties()).resolve();
        if (result != null) {
          superClasses.add(result);
        }
      }
    }
    return PsiUtilBase.toPsiElementArray(superClasses);
  }

  /* The implementation is manifestly lazy wrt psi scanning and uses stack rather sparingly.
   It must be more efficient on deep and wide hierarchies, but it was more fun than efficiency that produced it.
   */
  public Iterable<PyClassRef> iterateAncestors() {
    return new AncestorsIterable(this);
  }

  @Override
  public Iterable<PyClass> iterateAncestorClasses() {
    return new AncestorClassesIterable(this);
  }

  public boolean isSubclass(PyClass parent) {
    if (this == parent) {
      return true;
    }
    for (PyClass superclass : iterateAncestorClasses()) {
      if (parent == superclass) return true;
    }
    return false;
  }

  @Override
  public boolean isSubclass(String superClassQName) {
    if (getQualifiedName().equals(superClassQName)) {
      return true;
    }
    for (PyClassRef superclass : iterateAncestors()) {
      if (superClassQName.equals(superclass.getQualifiedName())) return true;
    }
    return false;
  }

  public PyDecoratorList getDecoratorList() {
    return childToPsi(PyElementTypes.DECORATOR_LIST);
  }

  public String getQualifiedName() {
    String name = getName();
    final PyClassStub stub = getStub();
    PsiElement ancestor = stub != null ? stub.getParentStub().getPsi() : getParent();
    while(!(ancestor instanceof PsiFile)) {
      if (ancestor == null) return name;    // can this happen?
      if (ancestor instanceof PyClass) {
        name = ((PyClass)ancestor).getName() + "." + name;
      }
      ancestor = stub != null ? ((StubBasedPsiElement) ancestor).getStub().getParentStub().getPsi() : ancestor.getParent();
    }

    PsiFile psiFile = ((PsiFile) ancestor).getOriginalFile();
    final PyFile builtins = PyBuiltinCache.getInstance(this).getBuiltinsFile();
    if (!psiFile.equals(builtins)) {
      VirtualFile vFile = psiFile.getVirtualFile();
      if (vFile != null) {
        final String packageName = ResolveImportUtil.findShortestImportableName(this, vFile);
        return packageName + "." + name;
      }
    }
    return name;
  }

  public boolean isTopLevel() {
    return getParentByStub() instanceof PsiFile;
  }

  @Override
  public List<String> getSlots() {
    List<String> slots = getOwnSlots();
    if (slots != null) {
      return slots;
    }
    for(PyClass cls: iterateAncestorClasses()) {
      slots = ((PyClassImpl) cls).getOwnSlots();
      if (slots != null) {
        return slots;
      }
    }
    return null;
  }

  @Nullable
  public List<String> getOwnSlots() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getSlots();
    }
    return PyFileImpl.getStringListFromTargetExpression(PyNames.SLOTS, getClassAttributes());
  }

  protected List<PyClassRef> getSuperClassesList() {
    if (PyNames.FAKE_OLD_BASE.equals(getName())) {
      return Collections.emptyList();
    }

    List<PyClassRef> result = resolveSuperClassesFromStub();
    if (result == null) {
      result = new ArrayList<PyClassRef>();
      PsiElement[] superClassElements = getSuperClassElements();
      for (PsiElement element : superClassElements) {
        result.add(new PyClassRef(element));
      }
    }

    if (result.size() == 0 && !PyBuiltinCache.getInstance(this).hasInBuiltins(this)) {
      String implicitSuperclassName = LanguageLevel.forElement(this).isPy3K() ? PyNames.OBJECT : PyNames.FAKE_OLD_BASE;
      PyClass implicitSuperclass = PyBuiltinCache.getInstance(this).getClass(implicitSuperclassName);
      if (implicitSuperclass != null) {
        result.add(new PyClassRef(implicitSuperclass));
      }
    }

    return result;
  }

  @Nullable
  private List<PyClassRef> resolveSuperClassesFromStub() {
    final PyClassStub stub = getStub();
    if (stub == null) {
      return null;
    }
    // stub-based resolve currently works correctly only with classes in file level
    final PsiElement parent = stub.getParentStub().getPsi();
    if (!(parent instanceof PyFile)) {
      // TODO[yole] handle this case
      return null;
    }

    List<PyClassRef> result = new ArrayList<PyClassRef>();
    for (PyQualifiedName qualifiedName : stub.getSuperClasses()) {
      result.add(classRefFromQName((NameDefiner)parent, qualifiedName));
    }
    return result;
  }

  private static PyClassRef classRefFromQName(NameDefiner parent, PyQualifiedName qualifiedName) {
    if (qualifiedName == null) {
      return new PyClassRef((String) null);
    }
    NameDefiner currentParent = parent;
    for (String component : qualifiedName.getComponents()) {
      PsiElement element = currentParent.getElementNamed(component);
      element = PyUtil.turnDirIntoInit(element);
      if (element instanceof PyImportElement) {
        element = ResolveImportUtil.resolveImportElement((PyImportElement)element);
      }
      if (!(element instanceof NameDefiner)) {
        return new PyClassRef(qualifiedName.toString());
      }
      currentParent = (NameDefiner)element;
    }

    return new PyClassRef(currentParent);
  }

  @NotNull
  public PyClass[] getSuperClasses() {
    final PyClassStub stub = getStub();
    if (stub != null) {
      final List<PyClassRef> pyClasses = resolveSuperClassesFromStub();
      if (pyClasses == null) {
        return EMPTY_ARRAY;
      }
      List<PyClass> result = new ArrayList<PyClass>();
      for (PyClassRef clsRef : pyClasses) {
        PyClass pyClass = clsRef.getPyClass();
        if (pyClass != null) {
          result.add(pyClass);
        }
      }
      return result.toArray(new PyClass[result.size()]);
    }

    PsiElement[] superClassElements = getSuperClassElements();
    if (superClassElements.length > 0) {
      List<PyClass> result = new ArrayList<PyClass>();
      for(PsiElement element: superClassElements) {
        if (element instanceof PyClass) {
          result.add((PyClass) element);
        }
      }
      return result.toArray(new PyClass[result.size()]);
    }
    return EMPTY_ARRAY;
  }

  @NotNull
  public PyFunction[] getMethods() {
    return getClassChildren(PyElementTypes.FUNCTION_DECLARATION, PyFunction.ARRAY_FACTORY);
  }

  @Override
  public PyClass[] getNestedClasses() {
    return getClassChildren(PyElementTypes.CLASS_DECLARATION, PyClass.ARRAY_FACTORY);
  }

  private <T extends PsiElement> T[] getClassChildren(IElementType elementType, ArrayFactory<T> factory) {
    // TODO: gather all top-level functions, maybe within control statements
    final PyClassStub classStub = getStub();
    if (classStub != null) {
      return classStub.getChildrenByType(elementType, factory);
    }
    List<T> result = new ArrayList<T>();
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (element.getNode().getElementType() == elementType) {
        //noinspection unchecked
        result.add((T) element);
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
      String fname = target.getName();
      for (String name: myNames) {
        if (name.equals(fname)) {
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
    if (isNewStyleClass()) proc = new NameFinder<PyFunction>(PyNames.INIT, PyNames.NEW);
    else proc = new NameFinder<PyFunction>(PyNames.INIT);
    visitMethods(proc, inherited);
    return proc.getResult();
  }

  private final static Maybe<PyFunction> unknown_call = new Maybe<PyFunction>(); // denotes _not_ a PyFunction, actually
  private final static Maybe<PyFunction> none = new Maybe<PyFunction>(null); // denotes an explicit None


  /**
   * @param name_filter returns true if property's name is acceptable
   * @param property_filter returns true if the property is acceptable
   * @param advanced is @foo.setter syntax allowed
   * @return the first property that both filters accepted.
   */
  @Nullable
  private Property findPropertyLocally(@Nullable Processor<String> name_filter, @Nullable Processor<Property> property_filter, boolean advanced) {
    // NOTE: fast enough to be rerun every time
    Property prop = lookInDecoratedProperties(name_filter, property_filter, advanced);
    if (prop != null) return prop;
    if (getStub() != null) {
      prop = lookInStubProperties(name_filter, property_filter);
      if (prop != null) return prop;
    }
    else {
      // name = property(...) assignments from PSI
      for (PyTargetExpression target : getClassAttributes()) {
        if (name_filter == null || name_filter.process(target.getName())) {
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
  private Property lookInDecoratedProperties(@Nullable Processor<String> name_filter, @Nullable Processor<Property> property_filter, boolean advanced) {
    // look at @property decorators
    Map<String, List<PyFunction>> grouped = new HashMap<String, List<PyFunction>>();
    // group suitable same-named methods, each group defines a property
    for (PyFunction method : getMethods()) {
      final String name = method.getName();
      if (name_filter == null || name_filter.process(name)) {
        List<PyFunction> bucket = grouped.get(name);
        if (bucket == null) {
          bucket = new SmartList<PyFunction>();
          grouped.put(name, bucket);
        }
        bucket.add(method);
      }
    }
    for (Map.Entry<String, List<PyFunction>> entry: grouped.entrySet()) {
      Maybe<PyFunction> getter = none;
      Maybe<PyFunction> setter = none;
      Maybe<PyFunction> deleter = none;
      String doc = null;
      final String name = entry.getKey();
      for (PyFunction method : entry.getValue()) {
        PyDecoratorList decolist = method.getDecoratorList();
        if (decolist != null) {
          for (PyDecorator deco : decolist.getDecorators()) {
            PyQualifiedName deco_name = deco.getQualifiedName();
            if (deco_name != null) {
              if (deco_name.matches(PyNames.PROPERTY)) {
                getter = new Maybe<PyFunction>(method);
              }
              else if (advanced && deco_name.matches(name, "setter")) {
                setter = new Maybe<PyFunction>(method);
              }
              else if (advanced && deco_name.matches(name, "deleter")) {
                deleter = new Maybe<PyFunction>(method);
              }
            }
          }
        }
        if (getter != none && setter != none && deleter != none) break; // can't improve
      }
      if (getter != none || setter != none || deleter != none) {
        final PropertyImpl prop = new PropertyImpl(getter, setter, deleter, doc, null);
        if (property_filter == null || property_filter.process(prop)) return prop;
      }
    }
    return null;
  }

  private Maybe<PyFunction> fromPacked(Maybe<String> maybe_name) {
    if (maybe_name.isDefined()) {
      PyFunction method = findMethodByName(maybe_name.value(), true);
      if (method != null) return new Maybe<PyFunction>(method);
    }
    return unknown_call;
  }

  @Nullable
  private Property lookInStubProperties(@Nullable Processor<String> name_filter, @Nullable Processor<Property> property_filter) {
    Maybe<PyFunction> getter = none;
    Maybe<PyFunction> setter = none;
    Maybe<PyFunction> deleter = none;
    String doc = null;
    final PyClassStub stub = getStub();
    if (stub != null) {
      for (StubElement substub : stub.getChildrenStubs()) {
        if (substub.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
          final PyTargetExpressionStub target_stub = (PyTargetExpressionStub)substub;
          PropertyStubStorage prop = target_stub.getCustomStub(PropertyStubStorage.class);
          if (prop != null && (name_filter == null || name_filter.process(target_stub.getName()))) {
            getter = fromPacked(prop.getGetter());
            setter = fromPacked(prop.getSetter());
            deleter = fromPacked(prop.getDeleter());
            doc = prop.getDoc();
          }
        }
      }
      if (getter != none || setter != none || deleter != none) {
        final PropertyImpl prop = new PropertyImpl(getter, setter, deleter, doc, null);
        if (property_filter == null || property_filter.process(prop)) return prop;
      }
    }
    return null;
  }

  public Property findProperty(@NotNull final String name) {
    LanguageLevel level = LanguageLevel.getDefault();
    final PsiFile containing_file = getContainingFile();
    if (containing_file != null) {
      final VirtualFile vfile = containing_file.getVirtualFile();
      if (vfile != null) level = LanguageLevel.forFile(vfile);
    }
    boolean use_advanced_syntax = level.isAtLeast(LanguageLevel.PYTHON26);
    Processor<String> name_filer = new Processor<String>() {
      @Override public boolean process(String s) { return name.equals(s); }
    };
    Property prop = findPropertyLocally(name_filer, null, use_advanced_syntax);
    if (prop != null) return prop;
    for (PyClass cls : iterateAncestorClasses()) {
      prop = ((PyClassImpl)cls).findPropertyLocally(name_filer, null, use_advanced_syntax);
      if (prop != null) return prop;
    }
    return null;
  }


  // NOTE: we might accept a name filter, too
  @Nullable
  public Property scanProperties(Processor<Property> processor, boolean inherited) {
    LanguageLevel level = LanguageLevel.getDefault();
    final PsiFile containing_file = getContainingFile();
    if (containing_file != null) {
      final VirtualFile vfile = containing_file.getVirtualFile();
      if (vfile != null) level = LanguageLevel.forFile(vfile);
    }
    boolean use_advanced_syntax = level.isAtLeast(LanguageLevel.PYTHON26);
    Property property = findPropertyLocally(null, processor, use_advanced_syntax);
    if (property != null) return property;
    if (inherited) {
      for (PyClass cls : iterateAncestorClasses()) {
        property = ((PyClassImpl)cls).findPropertyLocally(null, processor, use_advanced_syntax);
        if (property != null) return property;
      }
    }
    return null;
  }

  private static class PropertyImpl extends PropertyBunch<PyFunction> implements Property {

    private PropertyImpl(Maybe<PyFunction> getter, Maybe<PyFunction> setter, Maybe<PyFunction> deleter, String doc, PyTargetExpression site) {
      myDeleter = deleter;
      myGetter = getter;
      mySetter = setter;
      myDoc = doc;
      mySite = site;
    }

    @NotNull
    public Maybe<PyFunction> getDeleter() {
      return myDeleter;
    }

    @Override
    @NotNull
    public Maybe<PyFunction> getSetter() {
      return mySetter;
    }

    @Override
    @NotNull
    public Maybe<PyFunction> getGetter() {
      return myGetter;
    }

    public String getDoc() {
      return myDoc;
    }

    public PyTargetExpression getDefinitionSite() {
      return mySite;
    }

    @NotNull
    @Override
    public Maybe<PyFunction> getByDirection(@NotNull AccessDirection direction) {
      switch (direction) {
        case READ:    return myGetter;
        case WRITE:   return mySetter;
        case DELETE:  return myDeleter;
      }
      throw new IllegalArgumentException("Unknown direction " + PyUtil.nvl(direction));
    }

    @Override
    protected PyFunction translate(@NotNull PyReferenceExpression ref) {
      if (PyNames.NONE.equals(ref.getName())) return null; // short-circuit a common case
      PsiElement something = ref.getReference().resolve();
      if (something instanceof PyFunction) {
        return (PyFunction)something;
      }
      return null;
    }

    private static String nvl(Object s) {
      return s == null? "null" : s.toString();
    }

    public String toString() {
      // for debug
      return
        new StringBuilder("property(")
         .append(nvl(myGetter)).append(", ")
         .append(nvl(mySetter)).append(", ")
         .append(nvl(myDeleter)).append(", ")
         .append(nvl(myDoc)).append(")")
         .toString()
      ;
    }

    @Nullable
    public static PropertyImpl fromTarget(PyTargetExpression target) {
      PyExpression expr = target.findAssignedValue();
      final PropertyImpl prop = new PropertyImpl(null, null, null, null, target);
      final boolean success = fillFromCall(expr, prop);
      return success? prop : null;
    }
  }

  public boolean visitMethods(Processor<PyFunction> processor, boolean inherited) {
    PyFunction[] methods = getMethods();
    for(PyFunction method: methods) {
      if (! processor.process(method)) return false;
    }
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
        if (!ancestor.visitMethods(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitNestedClasses(Processor<PyClass> processor, boolean inherited) {
    PyClass[] nestedClasses = getNestedClasses();
    for (PyClass nestedClass : nestedClasses) {
      if (!processor.process(nestedClass)) return false;
    }
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
        if (!((PyClassImpl) ancestor).visitNestedClasses(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitClassAttributes(Processor<PyTargetExpression> processor, boolean inherited) {
    List<PyTargetExpression> methods = getClassAttributes();
    for(PyTargetExpression attribute: methods) {
      if (! processor.process(attribute)) return false;
    }
    if (inherited) {
      for (PyClass ancestor : iterateAncestorClasses()) {
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
            result.add((PyTargetExpression) target);
          }
        }
      }
    }
    return result;
  }

  @Override
  public PyTargetExpression findClassAttribute(String name, boolean inherited) {
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

  private List<PyTargetExpression> collectInstanceAttributes() {
    Map<String, PyTargetExpression> result = new HashMap<String, PyTargetExpression>();

    // __init__ takes priority over all other methods
    PyFunctionImpl initMethod = (PyFunctionImpl)findMethodByName(PyNames.INIT, false);
    if (initMethod != null) {
      collectInstanceAttributes(initMethod, result);
    }
    final PyFunction[] methods = getMethods();
    for (PyFunction method : methods) {
      if (!PyNames.INIT.equals(method.getName())) {
        collectInstanceAttributes(method, result);
      }
    }

    final Collection<PyTargetExpression> expressions = result.values();
    return new ArrayList<PyTargetExpression>(expressions);
  }

  private static void collectInstanceAttributes(PyFunction method, final Map<String, PyTargetExpression> result) {
    final PyParameter[] params = method.getParameterList().getParameters();
    if (params.length == 0) {
      return;
    }
    final String selfName = params [0].getName();

    final PyFunctionStub methodStub = method.getStub();
    if (methodStub != null) {
      final PyTargetExpression[] targets = methodStub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
      for (PyTargetExpression target : targets) {
        if (!result.containsKey(target.getName())) {
          result.put(target.getName(), target);
        }
      }
    }
    else {
      // NOTE: maybe treeCrawlUp would be more precise, but currently it works well enough; don't care.
      method.getStatementList().accept(new PyRecursiveElementVisitor() {
        public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
          super.visitPyAssignmentStatement(node);
          final PyExpression[] targets = node.getTargets();
          for (PyExpression target : targets) {
            if (PyUtil.isInstanceAttribute(target) && !result.containsKey(target.getName())) {
              result.put(target.getName(), (PyTargetExpression) target);
            }
          }
        }
      });
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
    PyClass objclass = PyBuiltinCache.getInstance(this).getClass("object");
    if (this == objclass) return true; // a rare but possible case
    if (hasNewStyleMetaClass(this)) return true;
    for (PyClassRef ancestor : iterateAncestors()) {
      PyClass pyClass = ancestor.getPyClass();
      if (pyClass == null) {
        // unknown, assume new-style class
        return true;
      }
      if (pyClass == objclass) return true;
      if (hasNewStyleMetaClass(pyClass)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNewStyleMetaClass(PyClass pyClass) {
    final PsiFile containingFile = pyClass.getContainingFile();
    if (containingFile instanceof PyFile) {
      final PsiElement element = ((PyFile)containingFile).findExportedName(PyNames.METACLASS);
      if (element instanceof PyTargetExpression) {
        final PyQualifiedName qName = ((PyTargetExpression)element).getAssignedQName();
        if (qName != null && qName.matches("type")) {
          return true;
        }
      }
    }
    if (pyClass.findClassAttribute(PyNames.METACLASS, false) != null) {
      return true;
    }
    return false;
  }

  public void processDeclarations(@NotNull PsiScopeProcessor processor, @Nullable PyExpression location) {
    if (!processClassLevelDeclarations(processor)) return;
    if (!processInstanceLevelDeclarations(processor, location)) return;
    processor.execute(this, ResolveState.initial());
  }

  public boolean processClassLevelDeclarations(PsiScopeProcessor processor) {
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
      final PsiElement the_psi = getNode().getPsi();
      PyResolveUtil.treeCrawlUp(processor, true, the_psi, the_psi);
    }
    return true;
  }

  public boolean processInstanceLevelDeclarations(PsiScopeProcessor processor, @Nullable PyExpression location) {
    Map<String, PyTargetExpression> declarationsInMethod = new HashMap<String, PyTargetExpression>();
    PyFunction instanceMethod = PsiTreeUtil.getParentOfType(location, PyFunction.class);
    if (instanceMethod != null && instanceMethod.getContainingClass() == this) {
      collectInstanceAttributes(instanceMethod, declarationsInMethod);
      for (PyTargetExpression targetExpression : declarationsInMethod.values()) {
        if (!processor.execute(targetExpression, ResolveState.initial())) {
          return false;
        }
      }
    }
    for(PyTargetExpression expr: getInstanceAttributes()) {
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
    return PythonDocStringFinder.find(getStatementList());
  }

  public String toString() {
    return "PyClass: " + getName();
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return Collections.<PyElement>singleton(this);
  }

  public PyElement getElementNamed(final String the_name) {
    return the_name.equals(getName())? this: null;
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
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(this, ScopeOwner.class);
    if (scopeOwner instanceof PyFunction) {
      return new LocalSearchScope(scopeOwner);
    }
    return super.getUseScope();
  }

  private static class AncestorsIterable implements Iterable<PyClassRef> {
    private final PyClassImpl myClass;

    public AncestorsIterable(final PyClassImpl pyClass) {
      myClass = pyClass;
    }

    public Iterator<PyClassRef> iterator() {
      return new AncestorsIterator(myClass);
    }
  }

  private static class AncestorsIterator implements Iterator<PyClassRef> {
    List<PyClassRef> pending = new LinkedList<PyClassRef>();
    private final Set<PyClassRef> seen;
    Iterator<PyClassRef> percolator;
    PyClassRef prefetch = null;
    private final PyClassImpl myAClass;

    public AncestorsIterator(PyClassImpl aClass) {
      myAClass = aClass;
      percolator = myAClass.getSuperClassesList().iterator();
      seen = new HashSet<PyClassRef>();
    }

    private AncestorsIterator(PyClassImpl AClass, Set<PyClassRef> seen) {
      myAClass = AClass;
      this.seen = seen;
      percolator = myAClass.getSuperClassesList().iterator();
    }

    public boolean hasNext() {
      // due to already-seen filtering, there's no way but to try and see.
      if (prefetch != null) return true;
      prefetch = getNext();
      return prefetch != null;
    }

    public PyClassRef next() {
      final PyClassRef nextClass = getNext();
      if (nextClass == null) throw new NoSuchElementException();
      return nextClass;
    }

    @Nullable
    private PyClassRef getNext() {
      iterations:
      while (true) {
        if (prefetch != null) {
          PyClassRef ret = prefetch;
          prefetch = null;
          return ret;
        }
        if (percolator.hasNext()) {
          PyClassRef it = percolator.next();
          if (seen.contains(it)) {
            continue iterations; // loop back is equivalent to return next();
          }
          pending.add(it);
          seen.add(it);
          return it;
        }
        else {
          while (pending.size() > 0) {
            PyClassRef it = pending.get(0);
            pending.remove(0);
            PyClass pyClass = it.getPyClass();
            if (pyClass != null) {
              percolator = new AncestorsIterator((PyClassImpl)pyClass, new HashSet<PyClassRef>(seen));
              continue iterations;
            }
          }
          return null;
        }
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static class AncestorClassesIterable implements Iterable<PyClass> {
    private final PyClassImpl myClass;

    public AncestorClassesIterable(final PyClassImpl pyClass) {
      myClass = pyClass;
    }

    public Iterator<PyClass> iterator() {
      return new AncestorClassesIterator(new AncestorsIterator(myClass));
    }
  }

  private static class AncestorClassesIterator implements Iterator<PyClass> {
    private final AncestorsIterator myAncestorsIterator;
    private PyClass myNext;

    public AncestorClassesIterator(AncestorsIterator ancestorsIterator) {
      myAncestorsIterator = ancestorsIterator;
    }

    @Override
    public boolean hasNext() {
      if (myNext != null) {
        return true;
      }
      while (myAncestorsIterator.hasNext()) {
        PyClassRef clsRef = myAncestorsIterator.getNext();
        if (clsRef == null) {
          return false;
        }
        myNext = clsRef.getPyClass();
        if (myNext != null) {
          return true;
        }
      }
      return false;
    }

    @Override
    public PyClass next() {
      if (myNext == null) {
        if (!hasNext()) return null;
      }
      PyClass next = myNext;
      myNext = null;
      return next;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }


}
