package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.lang.ASTNode;
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
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowBuilder;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.toolbox.SingleIterable;
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
    final ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
    getNode().replaceChild(findNameIdentifier(), nameElement);
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
      ASTNode node = findNameIdentifier();
      return node != null ? node.getText() : null;
    }
  }

  private ASTNode findNameIdentifier() {
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
        final PsiElement result = ref.resolve();
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
    return childToPsi(this, PyElementTypes.DECORATOR_LIST);
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

    final String packageName = ResolveImportUtil.findShortestImportableName(this, ((PsiFile)ancestor).getVirtualFile());
    return packageName + "." + name;
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
        element = PyReferenceExpressionImpl.turnDirIntoInit(element);
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

  public PyFunction findMethodByName(@NotNull final String name, boolean inherited) {
    PyFunction[] methods = getMethods();
    for(PyFunction method: methods) {
      if (name.equals(method.getName())) {
        return method;
      }
    }
    if (inherited) {
      for (PyClass ancestor : iterateAncestors()) {
        PyFunction candidate = ancestor.findMethodByName(name, false); // not recursively, we want MRI in MI cases 
        if (candidate != null) return candidate;
      }
    }
    return null;
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
    final ASTNode name = findNameIdentifier();
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
    return new SingleIterable<PyElement>(this);
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
    private PyClassImpl myClass;

    public AncestorsIterable(final PyClassImpl pyClass) {
      myClass = pyClass;
    }

    public Iterator<PyClass> iterator() {
      return new AncestorsIterator(myClass);
    }
  }

  private static class AncestorsIterator implements Iterator<PyClass> {
    List<PyClass> pending = new LinkedList<PyClass>();
    Set<PyClass> seen = new HashSet<PyClass>();
    Iterator<PyClass> percolator;
    PyClass prefetch = null;
    private PyClassImpl myAClass;

    public AncestorsIterator(PyClassImpl aClass) {
      myAClass = aClass;
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
          PyClass it = percolator.next();
          if (seen.contains(it)) {
            continue iterations; // loop back is equivalent to return next();
          }
          pending.add(it);
          seen.add(it);
          return it;
        }
        else if (pending.size() > 0) {
          PyClass it = pending.get(0);
          pending.remove(0); // t, ts* = pending
          percolator = it.iterateAncestors().iterator();
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
