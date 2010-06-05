package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowBuilder;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
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

  private PyTargetExpression[] myInstanceAttributes;

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

  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Override
  public Icon getIcon(int flags) {
    return Icons.CLASS_ICON;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClass(this);
  }

  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }

  @NotNull
  public PyExpression[] getSuperClassExpressions() {
    final PyArgumentList argList = PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
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
        final PsiElement result = ref.getReference(PyResolveContext.noImplicits()).resolve();
        if (result != null) {
          superClasses.add(result);
        }
      }
    }
    return superClasses.toArray(new PsiElement[superClasses.size()]);
  }

  /* The implementation is manifestly lazy wrt psi scanning and uses stack rather sparingly.
   It must be more efficient on deep and wide hierarchies, but it was more fun than efficiency that produced it.
   */
  public Iterable<PyClass> iterateAncestors() {
    return new AncestorsIterable(this);
  }

  public boolean isSubclass(PyClass parent) {
    if (this == parent) return true;
    for (PyClass superclass : iterateAncestors()) {
      if (parent == superclass) return true;
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

  protected List<PyClass> getSuperClassesList() {
    if (PyNames.FAKE_OLD_BASE.equals(getName())) {
      return Collections.emptyList();
    }

    List<PyClass> stubSuperClasses = resolveSuperClassesFromStub();
    if (stubSuperClasses != null) {
      return stubSuperClasses;
    }

    PsiElement[] superClassElements = getSuperClassElements();
    if (superClassElements.length > 0) {
      List<PyClass> result = new ArrayList<PyClass>();
      // maybe a bare old-style class?
      // TODO: depend on language version: py3k does not do old style classes
      PsiElement paren = PsiTreeUtil.getChildOfType(this, PyArgumentList.class).getFirstChild(); // no NPE, we always have the par expr
      if (paren != null && "(".equals(paren.getText())) { // "()" after class name, it's new style
        for (PsiElement element : superClassElements) {
          if (element instanceof PyClass) {
            result.add((PyClass)element);
          }
        }
      }
      else if (!PyBuiltinCache.BUILTIN_FILE.equals(getContainingFile().getName())) { // old-style *and* not builtin object()
        PyClass oldstyler = PyBuiltinCache.getInstance(this).getClass(PyNames.FAKE_OLD_BASE);
        if (oldstyler != null) result.add(oldstyler);
      }
      return result;
    }
    return Collections.emptyList();
  }

  @Nullable
  private List<PyClass> resolveSuperClassesFromStub() {
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

    List<PyClass> result = new ArrayList<PyClass>();
    for (PyQualifiedName qualifiedName : stub.getSuperClasses()) {
      if (qualifiedName == null) {
        return null;
      }

      NameDefiner currentParent = (NameDefiner) parent;
      for (String component : qualifiedName.getComponents()) {
        PsiElement element = currentParent.getElementNamed(component);
        element = PyUtil.turnDirIntoInit(element);
        if (element instanceof PyImportElement) {
          element = ResolveImportUtil.resolveImportElement((PyImportElement) element);
        }
        if (!(element instanceof NameDefiner)) {
          return null;
        }
        currentParent = (NameDefiner)element;
      }

      if (!(currentParent instanceof PyClass)) {
        return null;
      }
      result.add((PyClass) currentParent);
    }
    return result;
  }

  @NotNull
  public PyClass[] getSuperClasses() {
    List<PyClass> stubSuperClasses = resolveSuperClassesFromStub();
    if (stubSuperClasses != null) {
      return stubSuperClasses.toArray(new PyClass[stubSuperClasses.size()]);
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
    // TODO: gather all top-level functions, maybe within control statements
    final PyClassStub classStub = getStub();
    if (classStub != null) {
      return classStub.getChildrenByType(PyElementTypes.FUNCTION_DECLARATION, PyFunction.EMPTY_ARRAY);
    }
    List<PyFunction> result = new ArrayList<PyFunction>();
    final PyStatementList statementList = getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (element instanceof PyFunction) {
        result.add((PyFunction) element);
      }
    }
    return result.toArray(new PyFunction[result.size()]);
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

  public PyFunction findMethodByName(final String name, boolean inherited) {
    if (name == null) return null;
    NameFinder<PyFunction> proc = new NameFinder<PyFunction>(name);
    visitMethods(proc, inherited);
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

  @Nullable
  private Property findPropertyLocally(@NotNull String name) {
    // NOTE: maybe cache the result?
    Maybe<PyFunction> getter = none;
    Maybe<PyFunction> setter = none;
    Maybe<PyFunction> deleter = none;
    String doc = null;
    // look at @property decorators
    for (PyFunction method : getMethods()) {
      if (name.equals(method.getName())) {
        PyDecoratorList decolist = method.getDecoratorList();
        if (decolist != null) {
          for (PyDecorator deco : decolist.getDecorators()) {
            PyQualifiedName deco_name = deco.getQualifiedName();
            if (deco_name != null) {
              if (deco_name.matches("property")) {
                getter = new Maybe<PyFunction>(method);
              }
              else if (deco_name.matches(name, "setter")) {
                setter = new Maybe<PyFunction>(method);
              }
              else if (deco_name.matches(name, "deleter")) {
                deleter = new Maybe<PyFunction>(method);
              }
            }
          }
        }
      }
      if (getter != none && setter != none && deleter != none) break; // can't improve
    }
    if (getter != none || setter != none || deleter != none) return new PropertyImpl(getter, setter, deleter, doc, null);
    // look at name = property(...) assignments
    final PyClassStub stub = getStub();
    if (stub != null) {
      for (StubElement substub : stub.getChildrenStubs()) {
        if (substub.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
          final PyTargetExpressionStub target_stub = (PyTargetExpressionStub)substub;
          PropertyStubStorage prop = target_stub.getPropertyPack();
          if (prop != null && name.equals(target_stub.getName())) {
            getter = prop.getGetter().isDefined()? new Maybe<PyFunction>(findMethodByName(prop.getGetter().value(), true)) : unknown_call;
            setter = prop.getSetter().isDefined()? new Maybe<PyFunction>(findMethodByName(prop.getSetter().value(), true)): unknown_call;
            deleter = prop.getDeleter().isDefined()? new Maybe<PyFunction>(findMethodByName(prop.getDeleter().value(), true)) : unknown_call;
            doc = prop.getDoc();
          }
        }
      }
      if (getter != none || setter != none || deleter != none) return new PropertyImpl(getter, setter, deleter, doc, null);
    }
    else {
      // from PSI
      for (PyTargetExpression target : getClassAttributes()) {
        if (name.equals(target.getName())) {
          Property prop = PropertyImpl.fromTarget(target);
          if (prop != null) return prop;
        }
      }
    }
    return null;
  }

  public Property findProperty(@NotNull String name) {
    Property prop = findPropertyLocally(name);
    if (prop != null) return prop;
    for (PyClass cls : iterateAncestors()) {
      prop = ((PyClassImpl)cls).findPropertyLocally(name);
      if (prop != null) return prop;
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
      if (myDoc != null) return myDoc;
      if (myGetter.isDefined()) {
        final PyStringLiteralExpression doc_expr = ((PyFunction)myGetter.value()).getDocStringExpression();
        if (doc_expr != null) return doc_expr.getStringValue();
      }
      return null;
    }

    public PyTargetExpression getDefinitionSite() {
      return mySite;
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
      for (PyClass ancestor : iterateAncestors()) {
        if (!ancestor.visitMethods(processor, false)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean visitClassAttributes(Processor<PyTargetExpression> processor, boolean inherited) {
    PyTargetExpression[] methods = getClassAttributes();
    for(PyTargetExpression attribute: methods) {
      if (! processor.process(attribute)) return false;
    }
    if (inherited) {
      for (PyClass ancestor : iterateAncestors()) {
        if (!ancestor.visitClassAttributes(processor, false)) {
          return false;
        }
      }
    }
    return true;
    // NOTE: sorry, not enough metaprogramming to generalize visitMethods and visitClassAttributes 
  }



  public PyTargetExpression[] getClassAttributes() {
    PyClassStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY);
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
    return result.toArray(new PyTargetExpression[result.size()]);
  }

  public PyTargetExpression[] getInstanceAttributes() {
    if (myInstanceAttributes == null) {
      myInstanceAttributes = collectInstanceAttributes();
    }
    return myInstanceAttributes;
  }

  private PyTargetExpression[] collectInstanceAttributes() {
    Map<String, PyTargetExpression> result = new HashMap<String, PyTargetExpression>();

    // __init__ takes priority over all other methods
    PyFunctionImpl initMethod = (PyFunctionImpl)findMethodByName(PyNames.INIT, false);
    if (initMethod != null) {
      collectInstanceAttributes(initMethod, result);
    }
    final PyFunction[] methods = getMethods();
    for (PyFunction method : methods) {
      if (!PyNames.INIT.equals(method.getName())) {
        collectInstanceAttributes((PyFunctionImpl)method, result);
      }
    }

    final Collection<PyTargetExpression> expressions = result.values();
    return expressions.toArray(new PyTargetExpression[expressions.size()]);
  }

  private static void collectInstanceAttributes(PyFunctionImpl method, final Map<String, PyTargetExpression> result) {
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
            if (target instanceof PyTargetExpression) {
              final PyTargetExpression targetExpr = (PyTargetExpression)target;
              PyExpression qualifier = targetExpr.getQualifier();
              if (qualifier != null && qualifier.getText().equals(selfName) && !result.containsKey(targetExpr.getName())) {
                result.put(targetExpr.getName(), targetExpr);
              }
            }
          }
        }
      });
    }
  }

  public boolean isNewStyleClass() {
    PyClass objclass = PyBuiltinCache.getInstance(this).getClass("object");
    if (this == objclass) return true; // a rare but possible case
    for (PyClass ancestor : iterateAncestors()) {
      if (ancestor == objclass) return true;
    }
    return false;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place)
  {
    // class level
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

    // instance level
    for(PyTargetExpression expr: getInstanceAttributes()) {
      if (expr == lastParent) continue;
      if (!processor.execute(expr, substitutor)) return false;
    }
    //
    if (processor instanceof VariantsProcessor) {
      return true;
    }
    return processor.execute(this, substitutor);
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
    if (myControlFlowRef != null){
      myControlFlowRef.clear();
    }
    if (myScopeRef != null){
      myScopeRef.clear();
    }
    if (myInstanceAttributes != null) {
      myInstanceAttributes = null;
    }
  }

  private SoftReference<ControlFlow> myControlFlowRef;


  @NotNull
  public ControlFlow getControlFlow() {
    ControlFlow flow = getRefValue(myControlFlowRef);
    if (flow == null) {
      flow = new PyControlFlowBuilder().buildControlFlow(this);
      myControlFlowRef = new SoftReference<ControlFlow>(flow);
    }
    return flow;
  }

  private SoftReference<Scope> myScopeRef;

  @NotNull
  public Scope getScope() {
    Scope scope = getRefValue(myScopeRef);
    if (scope == null) {
      scope = new ScopeImpl(this);
      myScopeRef = new SoftReference<Scope>(scope);
    }
    return scope;
  }


  @Nullable
  private static<T> T getRefValue(final SoftReference<T> reference){
    return reference != null ? reference.get() : null;
  }

  private static class AncestorsIterable implements Iterable<PyClass> {
    private final PyClassImpl myClass;

    public AncestorsIterable(final PyClassImpl pyClass) {
      myClass = pyClass;
    }

    public Iterator<PyClass> iterator() {
      return new AncestorsIterator(myClass);
    }
  }

  private static class AncestorsIterator implements Iterator<PyClass> {
    List<PyClassImpl> pending = new LinkedList<PyClassImpl>();
    Set<PyClass> seen;
    Iterator<PyClass> percolator;
    PyClass prefetch = null;
    private final PyClassImpl myAClass;

    public AncestorsIterator(PyClassImpl aClass) {
      myAClass = aClass;
      percolator = myAClass.getSuperClassesList().iterator();
      seen = new HashSet<PyClass>();
    }

    private AncestorsIterator(PyClassImpl AClass, Set<PyClass> seen) {
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

    public PyClass next() {
      final PyClass nextClass = getNext();
      if (nextClass == null) throw new NoSuchElementException();
      return nextClass;
    }

    @Nullable
    private PyClass getNext() {
      iterations:
      while (true) {
        if (prefetch != null) {
          PyClass ret = prefetch;
          prefetch = null;
          return ret;
        }
        if (percolator.hasNext()) {
          PyClassImpl it = (PyClassImpl)percolator.next();
          if (seen.contains(it)) {
            continue iterations; // loop back is equivalent to return next();
          }
          pending.add(it);
          seen.add(it);
          return it;
        }
        else if (pending.size() > 0) {
          PyClassImpl it = pending.get(0);
          pending.remove(0); // t, ts* = pending
          percolator = new AncestorsIterator(it, new HashSet<PyClass>(seen));
          // loop back is equivalent to return next();
        }
        else return null;
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }


}
