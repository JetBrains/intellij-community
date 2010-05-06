package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowBuilder;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PyFileImpl extends PsiFileBase implements PyFile, PyExpression {
  protected PyType myType;
  private ThreadLocal<List<String>> myFindExportedNameStack = new ArrayListThreadLocal();
  private ThreadLocal<List<String>> myGetElementNamedStack = new ArrayListThreadLocal();

  public PyFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, PythonLanguage.getInstance());
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
    else fname = "(null)://" + ((Object)this).toString();
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
    if (virtualFile != null) {
      return LanguageLevel.forFile(virtualFile);
    }
    return LanguageLevel.getDefault();
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

    // if we're in a stmt (not place itself), try buitins:
    if (lastParent != null && ! substitutor.get(KEY_EXCLUDE_BUILTINS)) {
      final String fileName = getName();
      if (!fileName.equals("__builtin__.py")) {
        final PyFile builtins = PyBuiltinCache.getInstance(this).getBuiltinsFile();
        if (builtins != null && !builtins.processDeclarations(processor, substitutor, null, place)) return false;
      }
    }

    return true;
  }

  @Nullable
  public <T extends PyElement> T getContainingElement(Class<T> aClass) {
    return null;
  }

  @Nullable
  public PyElement getContainingElement(TokenSet tokenSet) {
    return null;
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
    return getTopLevelItems(PyElementTypes.CLASS_DECLARATION, PyClass.class);
  }

  public List<PyFunction> getTopLevelFunctions() {
    return getTopLevelItems(PyElementTypes.FUNCTION_DECLARATION, PyFunction.class);
  }

  public List<PyTargetExpression> getTopLevelAttributes() {
    return getTopLevelItems(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.class);
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
        for (Object child : children) {
          if (child instanceof NamedStub && name.equals(((NamedStub)child).getName())) {
            return ((NamedStub) child).getPsi();
          }
          else if (child instanceof PyFromImportStatementStub) {
            if (((PyFromImportStatementStub)child).isStarImport()) {
              final PyFromImportStatement statement = ((PyFromImportStatementStub)child).getPsi();
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
              final List<StubElement> importElements = ((StubElement)child).getChildrenStubs();
              for (StubElement importElement : importElements) {
                final PsiElement psi = importElement.getPsi();
                if (psi instanceof PyImportElement && name.equals(((PyImportElement)psi).getVisibleName())) {
                  return psi;
                }
              }
            }
          }
          else if (child instanceof PyImportStatementStub) {
            final List<StubElement> importElements = ((StubElement)child).getChildrenStubs();
            for (StubElement importElementStub : importElements) {
              final PsiElement psi = importElementStub.getPsi();
              if (psi instanceof PyImportElement) {
                final PyImportElement importElement = (PyImportElement)psi;
                final String asName = importElement.getAsName();
                if (asName != null && asName.equals(name)) {
                  return psi;
                }
                final PyQualifiedName qName = importElement.getImportedQName();
                if (qName != null && qName.getComponentCount() > 0) {
                  if (qName.getComponents().get(0).equals(name)) {
                    if (qName.getComponentCount() == 1) {
                      return psi;
                    }
                    return new PyImportedModule(this, PyQualifiedName.fromComponents(name));
                  }
                  if (name.equals(((PyImportElement)psi).getVisibleName())) {
                    return psi;
                  }
                }
              }
            }
          }
        }
        return null;
      }
      else {
        // dull plain resolve, as fast as stub index or better
        ResolveProcessor proc = new ResolveProcessor(name);
        PyResolveUtil.treeCrawlUp(proc, true, getLastChild());
        return proc.getResult();
      }
    }
    finally {
      stack.remove(name);
    }
  }

  @Nullable
  public PsiElement getElementNamed(String name) {
    final List<String> stack = myGetElementNamedStack.get();
    if (stack.contains(name)) {
      return null;
    }
    stack.add(name);
    try {
      PsiElement exportedName = findExportedName(name);
      if (exportedName == null) {
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
    throw new UnsupportedOperationException();
  }

  public boolean mustResolveOutside() {
    return false;
  }

  public List<PyImportElement> getImportTargets() {
    List<PyImportElement> ret = new ArrayList<PyImportElement>();
    List<PyImportStatement> imports = getTopLevelItems(PyElementTypes.IMPORT_STATEMENT, PyImportStatement.class);
    for (PyImportStatement one: imports) {
      ret.addAll(Arrays.asList(one.getImportElements()));
    }
    return ret;
  }
  
  public List<PyFromImportStatement> getFromImports() {
    return getTopLevelItems(PyElementTypes.FROM_IMPORT_STATEMENT, PyFromImportStatement.class);
  }

  private <T> List<T> getTopLevelItems(final IElementType elementType, final Class itemClass) {
    final List<T> result = new ArrayList<T>();
    final StubElement stub = getStub();
    if (stub != null) {
      final List<StubElement> children = stub.getChildrenStubs();
      for (StubElement child : children) {
        if (child.getStubType() == elementType) {
          //noinspection unchecked
          result.add((T)child.getPsi());
        }
      }
    }
    else {
      accept(new PyRecursiveElementVisitor() {
        public void visitPyElement(final PyElement node) {
          super.visitPyElement(node);
          checkAddElement(node);
        }

        public void visitPyClass(final PyClass node) {
          checkAddElement(node);  // do not recurse into functions
        }

        public void visitPyFunction(final PyFunction node) {
          checkAddElement(node);  // do not recurse into classes
        }

        private void checkAddElement(PsiElement node) {
          if (itemClass.isInstance(node)) {
            //noinspection unchecked
            result.add((T)node);
          }
        }
      });
    }
    return result;
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
    if (myControlFlowRef != null){
      myControlFlowRef.clear();
    }
    if (myScopeRef != null){
      myScopeRef.clear();
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

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return super.add(PyPsiUtils.removeIndentation(element));
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return super.addBefore(PyPsiUtils.removeIndentation(element), anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return super.addAfter(PyPsiUtils.removeIndentation(element), anchor);
  }

  private static class ArrayListThreadLocal extends ThreadLocal<List<String>> {
    @Override
    protected List<String> initialValue() {
      return new ArrayList<String>();
    }
  }
}
