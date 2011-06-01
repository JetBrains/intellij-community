package com.jetbrains.python.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.*;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class PyFileImpl extends PsiFileBase implements PyFile, PyExpression {
  protected PyType myType;
  private ThreadLocal<List<String>> myFindExportedNameStack = new ArrayListThreadLocal();
  private ThreadLocal<List<String>> myGetElementNamedStack = new ArrayListThreadLocal();

  private final CachedValue<List<PyImportElement>> myImportTargetsTransitive;
  //private volatile Boolean myAbsoluteImportEnabled;
  private final Map<FutureFeature, Boolean> myFutureFeatures;
  private List<String> myDunderAll;
  private boolean myDunderAllCalculated;

  public PyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, PythonLanguage.getInstance());
    myImportTargetsTransitive = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<PyImportElement>>() {
      @Override
      public Result<List<PyImportElement>> compute() {
        return new Result<List<PyImportElement>>(calculateImportTargetsTransitive(), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false);
    myFutureFeatures = new HashMap<FutureFeature, Boolean>();
  }

  @NotNull
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  public String toString() {
    return "PyFile:" + getName();
  }
  
  @NotNull
  public String getUrl() {
    String fname;
    VirtualFile vfile = getVirtualFile();
    if (vfile != null) fname = vfile.getUrl();
    else fname = "(null)://" + this.toString();
    return fname;
  }

  public PyFunction findTopLevelFunction(String name) {
    return findByName(name, getTopLevelFunctions());
  }

  public PyClass findTopLevelClass(String name) {
    return findByName(name, getTopLevelClasses());
  }

  public PyTargetExpression findTopLevelAttribute(String name) {
    return findByName(name, getTopLevelAttributes());
  }

  @Nullable
  private static <T extends PsiNamedElement> T findByName(String name, List<T> namedElements) {
    for (T namedElement : namedElements) {
      if (name.equals(namedElement.getName())) {
        return namedElement;
      }
    }
    return null;
  }

  public LanguageLevel getLanguageLevel() {
    if (myOriginalFile != null) {
      return ((PyFileImpl) myOriginalFile).getLanguageLevel();
    }
    VirtualFile virtualFile = getVirtualFile();

    if (virtualFile == null) {
      virtualFile = getUserData(FileBasedIndex.VIRTUAL_FILE);
    }
    if (virtualFile == null) {
      virtualFile = getViewProvider().getVirtualFile();
    }
    return LanguageLevel.forFile(virtualFile);
  }

  public Icon getIcon(int flags) {
    return PythonFileType.INSTANCE.getIcon();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof PyElementVisitor) {
      ((PyElementVisitor)visitor).visitPyFile(this);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for(PyClass c: getTopLevelClasses()) {
      if (c == lastParent) continue;
      if (!processor.execute(c, substitutor)) return false;
    }
    for(PyFunction f: getTopLevelFunctions()) {
      if (f == lastParent) continue;
      if (!processor.execute(f, substitutor)) return false;
    }
    for(PyTargetExpression e: getTopLevelAttributes()) {
      if (e == lastParent) continue;
      if (!processor.execute(e, substitutor)) return false;
    }

    for(PyImportElement e: getImportTargets()) {
      if (e == lastParent) continue;
      if (!processor.execute(e, substitutor)) return false;
    }

    for(PyFromImportStatement e: getFromImports()) {
      if (e == lastParent) continue;
      if (!e.processDeclarations(processor, substitutor, null, this)) return false;
    }

    return true;
  }

  public List<PyStatement> getStatements() {
    List<PyStatement> stmts = new ArrayList<PyStatement>();
    for (PsiElement child : getChildren()) {
      if (child instanceof PyStatement) {
        PyStatement statement = (PyStatement)child;
        stmts.add(statement);
      }
    }
    return stmts;
  }

  public List<PyClass> getTopLevelClasses() {
    return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.CLASS_DECLARATION, PyClass.class);
  }

  public List<PyFunction> getTopLevelFunctions() {
    return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.FUNCTION_DECLARATION, PyFunction.class);
  }

  public List<PyTargetExpression> getTopLevelAttributes() {
    return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.class);
  }

  public PsiElement findExportedName(String name) {
    final List<String> stack = myFindExportedNameStack.get();
    if (stack.contains(name)) {
      return null;
    }
    stack.add(name);
    try {
      final StubElement stub = getStub();
      if (stub != null) {
        final List children = stub.getChildrenStubs();
        final List<PyExceptPartStub> exceptParts = new ArrayList<PyExceptPartStub>();
        for (int i=children.size()-1; i >= 0; i--) {
          Object child = children.get(i);
          if (child instanceof PyExceptPartStub) {
            exceptParts.add((PyExceptPartStub) child);
          }
          else {
            PsiElement element = findNameInStub(child, name);
            if (element != null) {
              return element;
            }
          }
        }
        for (int i = exceptParts.size() - 1; i >= 0; i--) {
          PyExceptPartStub part = exceptParts.get(i);
          final List<StubElement> exceptChildren = part.getChildrenStubs();
          for (int j = exceptChildren.size() - 1; j >= 0; j--) {
            Object child = exceptChildren.get(j);
            PsiElement element = findNameInStub(child, name);
            if (element != null) {
              return element;
            }
          }
        }
      }
      else {
        // dull plain resolve, as fast as stub index or better
        ResolveProcessor proc = new ResolveProcessor(name);
        PyResolveUtil.treeCrawlUp(proc, true, getLastChild());
        if (proc.getResult() != null) {
          return proc.getResult();
        }
      }
      List<String> allNames = getDunderAll();
      if (allNames != null && allNames.contains(name)) {
        return findExportedName(PyNames.ALL);
      }
      return null;
    }
    finally {
      stack.remove(name);
    }
  }

  @Nullable
  private PsiElement findNameInStub(Object child, String name) {
    if (child instanceof NamedStub && name.equals(((NamedStub)child).getName())) {
      return ((NamedStub) child).getPsi();
    }
    else if (child instanceof PyFromImportStatementStub) {
      return findNameInFromImportStatementStub(name, (PyFromImportStatementStub)child);
    }
    else if (child instanceof PyImportStatementStub) {
      return findNameInImportStatementStub(name, (PyImportStatementStub)child);
    }
    return null;
  }

  @Nullable
  private PsiElement findNameInFromImportStatementStub(String name, PyFromImportStatementStub stub) {
    if (stub.isStarImport()) {
      if (PyUtil.isClassPrivateName(name)) {
        return null;
      }
      final PyFromImportStatement statement = stub.getPsi();
      PsiElement starImportSource = ResolveImportUtil.resolveFromImportStatementSource(statement);
      if (starImportSource != null) {
        starImportSource = PyUtil.turnDirIntoInit(starImportSource);
        if (starImportSource instanceof PyFile) {
          final PsiElement result = ((PyFile)starImportSource).getElementNamed(name);
          if (result != null) {
            return result;
          }
        }
      }
    }
    else {
      final List<StubElement> importElements = stub.getChildrenStubs();
      for (StubElement importElement : importElements) {
        final PsiElement psi = importElement.getPsi();
        if (psi instanceof PyImportElement && name.equals(((PyImportElement)psi).getVisibleName())) {
          final PsiElement resolved = ((PyImportElement) psi).getElementNamed(name);
          if (resolved != null) {
            return resolved;
          }
        }
      }
    }
    // http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
    if (PyNames.INIT_DOT_PY.equals(getName())) {
      final PyQualifiedName qName = stub.getImportSourceQName();
      if (qName.endsWith(name)) {
        final PsiElement element = PyUtil.turnInitIntoDir(ResolveImportUtil.resolveFromImportStatementSource(stub.getPsi()));
        if (element != null && element.getParent() == getContainingDirectory()) {
          return element;
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiElement findNameInImportStatementStub(String name, PyImportStatementStub child) {
    final List<StubElement> importElements = child.getChildrenStubs();
    for (StubElement importElementStub : importElements) {
      final PsiElement psi = importElementStub.getPsi();
      if (psi instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)psi;
        final String asName = importElement.getAsName();
        if (asName != null && asName.equals(name)) {
          final PsiElement resolved = importElement.getElementNamed(name);
          if (resolved != null) {
            return resolved;
          }
        }
        final PyQualifiedName qName = importElement.getImportedQName();
        if (qName != null && qName.getComponentCount() > 0) {
          if (qName.getComponents().get(0).equals(name)) {
            if (qName.getComponentCount() == 1) {
              return psi;
            }
            return new PyImportedModule(this, PyQualifiedName.fromComponents(name));
          }
          // http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
          if (qName.getComponentCount() > 1 && name.equals(qName.getLastComponent()) && PyNames.INIT_DOT_PY.equals(getName())) {
            final PsiElement element = ResolveImportUtil.resolveImportElement(importElement, qName.removeLastComponent());
            if (PyUtil.turnDirIntoInit(element) == this) {
              return importElement;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public PsiElement getElementNamed(String name) {
    return getElementNamed(name, true);
  }

  public PsiElement getElementNamed(String name, boolean withBuiltins) {
    final List<String> stack = myGetElementNamedStack.get();
    if (stack.contains(name)) {
      return null;
    }
    stack.add(name);
    try {
      PsiElement exportedName = findExportedName(name);
      if (exportedName == null && withBuiltins) {
        final PyFile builtins = PyBuiltinCache.getInstance(this).getBuiltinsFile();
        if (builtins != null && builtins != this) {
          exportedName = builtins.findExportedName(name);
        }
      }
      if (exportedName instanceof PyImportElement) {
        return ((PyImportElement) exportedName).getElementNamed(name);
      }
      return exportedName;
    }
    finally {
      stack.remove(name);
    }
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    final List<String> dunderAll = getDunderAll();
    final List<String> remainingDunderAll = dunderAll == null ? null : new ArrayList<String>(dunderAll);
    final List<PyElement> result = new ArrayList<PyElement>();
    VariantsProcessor processor = new VariantsProcessor(this) {
      @Override
      protected void addElement(String name, PsiElement element) {
        result.add((PyElement) element);
        if (remainingDunderAll != null) {
          remainingDunderAll.remove(name);
        }
      }
    };
    processor.setAllowedNames(dunderAll);
    processDeclarations(processor, ResolveState.initial(), null, this);
    if (remainingDunderAll != null) {
      for (String s: remainingDunderAll) {
        result.add(new LightNamedElement(myManager, PythonLanguage.getInstance(), s));
      }
    }
    return result;
  }

  public boolean mustResolveOutside() {
    return false;
  }

  public List<PyImportElement> getImportTargets() {
    List<PyImportElement> ret = new ArrayList<PyImportElement>();
    List<PyImportStatement> imports = PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.IMPORT_STATEMENT, PyImportStatement.class);
    for (PyImportStatement one: imports) {
      ContainerUtil.addAll(ret, one.getImportElements());
    }
    return ret;
  }

  public List<PyImportElement> getImportTargetsTransitive() {
    return myImportTargetsTransitive.getValue();
  }

  private List<PyImportElement> calculateImportTargetsTransitive() {
    Set<PyFile> visitedFiles = new HashSet<PyFile>();
    visitedFiles.add(this);
    List<PyImportElement> result = new ArrayList<PyImportElement>();
    calculateImportTargetsRecursive(this, visitedFiles, result);
    return result;
  }

  private static void calculateImportTargetsRecursive(PyFileImpl pyFile, Set<PyFile> visitedFiles, List<PyImportElement> result) {
    final List<PyImportElement> imports = pyFile.getImportTargets();
    for (PyImportElement anImport : imports) {
      result.add(anImport);
      final PsiElement resolveResult = ResolveImportUtil.resolveImportElement(anImport);
      if (resolveResult instanceof PyFileImpl) {
        PyFileImpl file = (PyFileImpl) resolveResult;
        if (!visitedFiles.contains(file)) {
          visitedFiles.add(file);
          calculateImportTargetsRecursive((PyFileImpl) resolveResult, visitedFiles, result);
        }
      }
    }
  }

  public List<PyFromImportStatement> getFromImports() {
    return PyPsiUtils.collectStubChildren(this, getStub(), PyElementTypes.FROM_IMPORT_STATEMENT, PyFromImportStatement.class);
  }

  @Override
  public List<String> getDunderAll() {
    final StubElement stubElement = getStub();
    if (stubElement instanceof PyFileStub) {
      return ((PyFileStub) stubElement).getDunderAll();
    }
    if (!myDunderAllCalculated) {
      final List<String> dunderAll = calculateDunderAll();
      myDunderAll = dunderAll == null ? null : Collections.unmodifiableList(dunderAll);
      myDunderAllCalculated = true;
    }
    return myDunderAll;
  }

  @Nullable
  public List<String> calculateDunderAll() {
    final DunderAllBuilder builder = new DunderAllBuilder();
    accept(builder);
    return builder.result();
  }

  private static class DunderAllBuilder extends PyRecursiveElementVisitor {
    private List<String> myResult = null;
    private boolean myDynamic = false;
    private boolean myFoundDunderAll = false;

    // hashlib builds __all__ by concatenating multiple lists of strings, and we want to understand this
    private Map<String, List<String>> myDunderLike = new HashMap<String, List<String>>();

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      if (PyNames.ALL.equals(node.getName())) {
        myFoundDunderAll = true;
        PyExpression value = node.findAssignedValue();
        if (value instanceof PyBinaryExpression) {
          PyBinaryExpression binaryExpression = (PyBinaryExpression)value;
          if (binaryExpression.isOperator("+")) {
            List<String> lhs = getStringListFromValue(binaryExpression.getLeftExpression());
            List<String> rhs = getStringListFromValue(binaryExpression.getRightExpression());
            if (lhs != null && rhs != null) {
              myResult = new ArrayList<String>(lhs);
              myResult.addAll(rhs);
            }
          }
        }
        else {
          myResult = PyUtil.getStringListFromTargetExpression(node);
        }
      }
      if (!myFoundDunderAll) {
        List<String> names = PyUtil.getStringListFromTargetExpression(node);
        if (names != null) {
          myDunderLike.put(node.getName(), names);
        }
      }
    }

    @Nullable
    private List<String> getStringListFromValue(PyExpression expression) {
      if (expression instanceof PyReferenceExpression && ((PyReferenceExpression)expression).getQualifier() == null) {
        return myDunderLike.get(((PyReferenceExpression)expression).getReferencedName());
      }
      return PyUtil.strListValue(expression);
    }

    @Override
    public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
      if (PyNames.ALL.equals(node.getTarget().getName())) {
        myDynamic = true;
      }
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
        if (qualifier != null && PyNames.ALL.equals(qualifier.getText())) {
          // TODO handle append and extend with constant arguments here
          myDynamic = true;
        }
      }
    }

    @Nullable
    List<String> result() {
      return myDynamic ? null : myResult;
    }
  }

  @Nullable
  public static List<String> getStringListFromTargetExpression(final String name, List<PyTargetExpression> attrs) {
    for (PyTargetExpression attr : attrs) {
      if (name.equals(attr.getName())) {
        return PyUtil.getStringListFromTargetExpression(attr);
      }
    }
    return null;
  }

  public boolean hasImportFromFuture(FutureFeature feature) {
    final StubElement stub = getStub();
    if (stub instanceof PyFileStub) {
      return ((PyFileStub) stub).getFutureFeatures().get(feature.ordinal());
    }
    Boolean enabled = myFutureFeatures.get(feature);
    if (enabled == null) {
      enabled = calculateImportFromFuture(feature);
      myFutureFeatures.put(feature, enabled);
      // NOTE: ^^^ not synchronized. if two threads will try to modify this, both can only be expected to set the same value.
    }
    return enabled;
  }

  public boolean calculateImportFromFuture(FutureFeature feature) {
    final List<PyFromImportStatement> fromImports = getFromImports();
    for (PyFromImportStatement fromImport : fromImports) {
      if (fromImport.isFromFuture()) {
        final PyImportElement[] pyImportElements = fromImport.getImportElements();
        for (PyImportElement element : pyImportElements) {
          final PyQualifiedName qName = element.getImportedQName();
          if (qName != null && qName.matches(feature.toString())) {
            return true;
          }
        }
      }
    }
    return false;
  }


  public PyType getType(@NotNull TypeEvalContext context) {
    if (myType == null) myType = new PyModuleType(this);
    return myType;
  }

  public PyStringLiteralExpression getDocStringExpression() {
    return PythonDocStringFinder.find(this);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
    myDunderAllCalculated = false;
    myFutureFeatures.clear(); // probably no need to synchronize
  }

  private static class ArrayListThreadLocal extends ThreadLocal<List<String>> {
    @Override
    protected List<String> initialValue() {
      return new ArrayList<String>();
    }
  }
}
