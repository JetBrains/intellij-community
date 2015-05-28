/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.QualifiedName;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.IndexingDataKeys;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.inspections.PythonVisitorFilter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

public class PyFileImpl extends PsiFileBase implements PyFile, PyExpression {
  protected PyType myType;
  private final ThreadLocal<List<String>> myFindExportedNameStack = new ArrayListThreadLocal();

  //private volatile Boolean myAbsoluteImportEnabled;
  private final Map<FutureFeature, Boolean> myFutureFeatures;
  private List<String> myDunderAll;
  private boolean myDunderAllCalculated;
  private volatile SoftReference<ExportedNameCache> myExportedNameCache = new SoftReference<ExportedNameCache>(null);
  private final PsiModificationTracker myModificationTracker;

  private class ExportedNameCache {
    private final Map<String, PsiElement> myLocalDeclarations = new HashMap<String, PsiElement>();
    private final MultiMap<String, PsiElement> myLocalAmbiguousDeclarations = new MultiMap<String, PsiElement>();
    private final Map<String, PsiElement> myExceptPartDeclarations = new HashMap<String, PsiElement>();
    private final MultiMap<String, PsiElement> myExceptPartAmbiguousDeclarations = new MultiMap<String, PsiElement>();
    private final List<PsiElement> myNameDefiners = new ArrayList<PsiElement>();
    private final List<String> myNameDefinerNegativeCache = new ArrayList<String>();
    private long myNameDefinerOOCBModCount = -1;
    private final long myModificationStamp;

    private ExportedNameCache(long modificationStamp) {
      myModificationStamp = modificationStamp;
      final List<PsiElement> children = PyPsiUtils.collectAllStubChildren(PyFileImpl.this, getStub());
      final List<PyExceptPart> exceptParts = new ArrayList<PyExceptPart>();
      for (PsiElement child : children) {
        if (child instanceof PyExceptPart) {
          exceptParts.add((PyExceptPart)child);
        }
        else {
          addDeclaration(child, myLocalDeclarations, myLocalAmbiguousDeclarations, myNameDefiners);
        }
      }
      if (!exceptParts.isEmpty()) {
        for (PyExceptPart part : exceptParts) {
          final List<PsiElement> exceptChildren = PyPsiUtils.collectAllStubChildren(part, part.getStub());
          for (PsiElement child : exceptChildren) {
            addDeclaration(child, myExceptPartDeclarations, myExceptPartAmbiguousDeclarations, myNameDefiners);
          }
        }
      }
    }

    private void addDeclaration(PsiElement child,
                                Map<String, PsiElement> localDeclarations,
                                MultiMap<String, PsiElement> ambiguousDeclarations,
                                List<PsiElement> nameDefiners) {
      if (child instanceof PyTargetExpression || child instanceof PyFunction || child instanceof PyClass) {
        final String name = ((PsiNamedElement)child).getName();
        localDeclarations.put(name, child);
      }
      else if (child instanceof PyFromImportStatement) {
        final PyFromImportStatement fromImportStatement = (PyFromImportStatement)child;
        if (fromImportStatement.isStarImport()) {
          nameDefiners.add(fromImportStatement);
        }
        else {
          for (PyImportElement importElement : fromImportStatement.getImportElements()) {
            addImportElementDeclaration(importElement, localDeclarations, ambiguousDeclarations);
          }
        }
      }
      else if (child instanceof PyImportStatement) {
        final PyImportStatement importStatement = (PyImportStatement)child;
        for (PyImportElement importElement : importStatement.getImportElements()) {
          addImportElementDeclaration(importElement, localDeclarations, ambiguousDeclarations);
        }
      }
      else if (child instanceof NameDefiner) {
        nameDefiners.add(child);
      }
    }

    private void addImportElementDeclaration(PyImportElement importElement,
                                             Map<String, PsiElement> localDeclarations,
                                             MultiMap<String, PsiElement> ambiguousDeclarations) {
      final String visibleName = importElement.getVisibleName();
      if (visibleName != null) {
        if (ambiguousDeclarations.containsKey(visibleName)) {
          ambiguousDeclarations.putValue(visibleName, importElement);
        }
        else if (localDeclarations.containsKey(visibleName)) {
          final PsiElement oldElement = localDeclarations.get(visibleName);
          ambiguousDeclarations.putValue(visibleName, oldElement);
          ambiguousDeclarations.putValue(visibleName, importElement);
        }
        else {
          localDeclarations.put(visibleName, importElement);
        }
      }
    }

    @Nullable
    private PsiElement resolve(String name) {
      final PsiElement named = resolveNamed(name, myLocalDeclarations, myLocalAmbiguousDeclarations);
      if (named != null) {
        return named;
      }
      if (!myNameDefiners.isEmpty()) {
        final PsiElement result = findNameInNameDefiners(name);
        if (result != null) {
          return result;
        }
      }
      return resolveNamed(name, myExceptPartDeclarations, myExceptPartAmbiguousDeclarations);
    }

    @Nullable
    private PsiElement resolveNamed(String name,
                                    final Map<String, PsiElement> declarations,
                                    final MultiMap<String, PsiElement> ambiguousDeclarations) {
      if (ambiguousDeclarations.containsKey(name)) {
        final List<PsiElement> localAmbiguous = new ArrayList<PsiElement>(myLocalAmbiguousDeclarations.get(name));
        for (int i = localAmbiguous.size() - 1; i >= 0; i--) {
          PsiElement ambiguous = localAmbiguous.get(i);
          final PsiElement result = resolveDeclaration(name, ambiguous, true);
          if (result != null) {
            return result;
          }
        }
      }
      else {
        final PsiElement result = declarations.get(name);
        if (result != null) {
          final boolean resolveImportElement = result instanceof PyImportElement &&
                                               ((PyImportElement)result).getContainingImportStatement() instanceof PyFromImportStatement;
          return resolveDeclaration(name, result, resolveImportElement);
        }
      }
      return null;
    }

    @Nullable
    private PsiElement resolveDeclaration(String name, PsiElement result, boolean resolveImportElement) {
      if (result instanceof PyImportElement) {
        final PyImportElement importElement = (PyImportElement)result;
        return findNameInImportElement(name, importElement, resolveImportElement);
      }
      else if (result instanceof PyFromImportStatement) {
        return ((PyFromImportStatement)result).resolveImportSource();
      }
      return result;
    }

    @Nullable
    private PsiElement findNameInNameDefiners(String name) {
      synchronized (myNameDefinerNegativeCache) {
        long oocbModCount = myModificationTracker.getOutOfCodeBlockModificationCount();
        if (oocbModCount != myNameDefinerOOCBModCount) {
          myNameDefinerNegativeCache.clear();
          myNameDefinerOOCBModCount = oocbModCount;
        }
        else {
          if (myNameDefinerNegativeCache.contains(name)) {
            return null;
          }
        }
      }
      for (PsiElement definer : myNameDefiners) {
        final PsiElement result;
        if (definer instanceof PyFromImportStatement) {
          result = findNameInStarImport(name, (PyFromImportStatement)definer);
        }
        else {
          result = ((NameDefiner)definer).getElementNamed(name);
        }
        if (result != null) {
          return result;
        }
      }
      synchronized (myNameDefinerNegativeCache) {
        myNameDefinerNegativeCache.add(name);
      }
      return null;
    }

    public long getModificationStamp() {
      return myModificationStamp;
    }
  }

  public PyFileImpl(FileViewProvider viewProvider) {
    this(viewProvider, PythonLanguage.getInstance());
  }

  public PyFileImpl(FileViewProvider viewProvider, Language language) {
    super(viewProvider, language);
    myFutureFeatures = new HashMap<FutureFeature, Boolean>();
    myModificationTracker = PsiModificationTracker.SERVICE.getInstance(getProject());
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  public String toString() {
    return "PyFile:" + getName();
  }

  @Override
  public PyFunction findTopLevelFunction(String name) {
    return findByName(name, getTopLevelFunctions());
  }

  @Override
  public PyClass findTopLevelClass(String name) {
    return findByName(name, getTopLevelClasses());
  }

  @Override
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

  @Override
  public LanguageLevel getLanguageLevel() {
    if (myOriginalFile != null) {
      return ((PyFileImpl)myOriginalFile).getLanguageLevel();
    }
    VirtualFile virtualFile = getVirtualFile();

    if (virtualFile == null) {
      virtualFile = getUserData(IndexingDataKeys.VIRTUAL_FILE);
    }
    if (virtualFile == null) {
      virtualFile = getViewProvider().getVirtualFile();
    }
    return PyUtil.getLanguageLevelForVirtualFile(getProject(), virtualFile);
  }

  @Override
  public Icon getIcon(int flags) {
    return PythonFileType.INSTANCE.getIcon();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (isAcceptedFor(visitor.getClass())) {
      if (visitor instanceof PyElementVisitor) {
        ((PyElementVisitor)visitor).visitPyFile(this);
      }
      else {
        super.accept(visitor);
      }
    }
  }

  public boolean isAcceptedFor(@NotNull Class visitorClass) {
    for (Language lang : getViewProvider().getLanguages()) {
      final List<PythonVisitorFilter> filters = PythonVisitorFilter.INSTANCE.allForLanguage(lang);
      for (PythonVisitorFilter filter : filters) {
        if (!filter.isSupported(visitorClass, this)) {
          return false;
        }
      }
    }
    return true;
  }

  private final Key<Set<PyFile>> PROCESSED_FILES = Key.create("PyFileImpl.processDeclarations.processedFiles");

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull ResolveState resolveState,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final List<String> dunderAll = getDunderAll();
    final List<String> remainingDunderAll = dunderAll == null ? null : new ArrayList<String>(dunderAll);
    PsiScopeProcessor wrapper = new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (!processor.execute(element, state)) return false;
        if (remainingDunderAll != null && element instanceof PyElement) {
          remainingDunderAll.remove(((PyElement)element).getName());
        }
        return true;
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return processor.getHint(hintKey);
      }

      @Override
      public void handleEvent(@NotNull Event event, @Nullable Object associated) {
        processor.handleEvent(event, associated);
      }
    };

    Set<PyFile> pyFiles = resolveState.get(PROCESSED_FILES);
    if (pyFiles == null) {
      pyFiles = new HashSet<PyFile>();
      resolveState = resolveState.put(PROCESSED_FILES, pyFiles);
    }
    if (pyFiles.contains(this)) return true;
    pyFiles.add(this);
    for (PyClass c : getTopLevelClasses()) {
      if (c == lastParent) continue;
      if (!wrapper.execute(c, resolveState)) return false;
    }
    for (PyFunction f : getTopLevelFunctions()) {
      if (f == lastParent) continue;
      if (!wrapper.execute(f, resolveState)) return false;
    }
    for (PyTargetExpression e : getTopLevelAttributes()) {
      if (e == lastParent) continue;
      if (!wrapper.execute(e, resolveState)) return false;
    }

    for (PyImportElement e : getImportTargets()) {
      if (e == lastParent) continue;
      if (!wrapper.execute(e, resolveState)) return false;
    }

    for (PyFromImportStatement e : getFromImports()) {
      if (e == lastParent) continue;
      if (!e.processDeclarations(wrapper, resolveState, null, this)) return false;
    }

    if (remainingDunderAll != null) {
      for (String s : remainingDunderAll) {
        if (!PyNames.isIdentifier(s)) {
          continue;
        }
        if (!processor.execute(new LightNamedElement(myManager, PythonLanguage.getInstance(), s), resolveState)) return false;
      }
    }
    return true;
  }

  @Override
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

  @Override
  public List<PyClass> getTopLevelClasses() {
    return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.CLASS_DECLARATION, PyClass.class);
  }

  @NotNull
  @Override
  public List<PyFunction> getTopLevelFunctions() {
    return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.FUNCTION_DECLARATION, PyFunction.class);
  }

  @Override
  public List<PyTargetExpression> getTopLevelAttributes() {
    return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.class);
  }

  @Nullable
  public PsiElement findExportedName(String name) {
    final List<String> stack = myFindExportedNameStack.get();
    if (stack.contains(name)) {
      return null;
    }
    stack.add(name);
    try {
      PsiElement result = getExportedNameCache().resolve(name);
      if (result != null) {
        return result;
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

  private ExportedNameCache getExportedNameCache() {
    ExportedNameCache cache;
    cache = myExportedNameCache != null ? myExportedNameCache.get() : null;
    final long modificationStamp = getModificationStamp();
    if (myExportedNameCache != null && cache != null && modificationStamp != cache.getModificationStamp()) {
      myExportedNameCache.clear();
      cache = null;
    }
    if (cache == null) {
      cache = new ExportedNameCache(modificationStamp);
      myExportedNameCache = new SoftReference<ExportedNameCache>(cache);
    }
    return cache;
  }

  @Nullable
  private PsiElement findNameInStarImport(String name, PyFromImportStatement statement) {
    if (PyUtil.isClassPrivateName(name)) {
      return null;
    }
    PsiElement starImportSource = statement.resolveImportSource();
    if (starImportSource != null) {
      starImportSource = PyUtil.turnDirIntoInit(starImportSource);
      if (starImportSource instanceof PyFile) {
        final PyFile file = (PyFile)starImportSource;
        final PsiElement result = file.getElementNamed(name);
        if (result != null && PyUtil.isStarImportableFrom(name, file)) {
          return result;
        }
      }
    }
    // http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
    if (PyNames.INIT_DOT_PY.equals(getName())) {
      final QualifiedName qName = statement.getImportSourceQName();
      if (qName != null && qName.endsWith(name)) {
        final PsiElement element = PyUtil.turnInitIntoDir(statement.resolveImportSource());
        if (element != null && element.getParent() == getContainingDirectory()) {
          return element;
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiElement findNameInImportElement(String name, PyImportElement importElement, final boolean resolveImportElement) {
    final PsiElement result = importElement.getElementNamed(name, resolveImportElement);
    if (result != null) {
      return result;
    }
    final QualifiedName qName = importElement.getImportedQName();
    // http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
    if (qName != null && qName.getComponentCount() > 1 && name.equals(qName.getLastComponent()) && PyNames.INIT_DOT_PY.equals(getName())) {
      final List<? extends RatedResolveResult> elements =
        ResolveImportUtil.resolveNameInImportStatement(importElement, qName.removeLastComponent());
      for (RatedResolveResult element : elements) {
        if (PyUtil.turnDirIntoInit(element.getElement()) == this) {
          return importElement;
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public PsiElement getElementNamed(String name) {
    PsiElement exportedName = findExportedName(name);
    if (exportedName instanceof PyImportElement) {
      PsiElement importedElement = ((PyImportElement)exportedName).getElementNamed(name);
      if (importedElement != null && !importedElement.isValid()) {
        throw new PsiInvalidElementAccessException(importedElement);
      }
      return importedElement;
    }
    else if (exportedName != null && !exportedName.isValid()) {
      throw new PsiInvalidElementAccessException(exportedName);
    }
    return exportedName;
  }

  @Override
  @NotNull
  public Iterable<PyElement> iterateNames() {
    final List<PyElement> result = new ArrayList<PyElement>();
    VariantsProcessor processor = new VariantsProcessor(this) {
      @Override
      protected void addElement(String name, PsiElement element) {
        element = PyUtil.turnDirIntoInit(element);
        if (element instanceof PyElement) {
          result.add((PyElement)element);
        }
      }
    };
    processor.setAllowedNames(getDunderAll());
    processDeclarations(processor, ResolveState.initial(), null, this);
    return result;
  }

  @Override
  public boolean mustResolveOutside() {
    return false;
  }

  @Override
  @NotNull
  public List<PyImportElement> getImportTargets() {
    List<PyImportElement> ret = new ArrayList<PyImportElement>();
    List<PyImportStatement> imports =
      PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.IMPORT_STATEMENT, PyImportStatement.class);
    for (PyImportStatement one : imports) {
      ContainerUtil.addAll(ret, one.getImportElements());
    }
    return ret;
  }

  @Override
  @NotNull
  public List<PyFromImportStatement> getFromImports() {
    return PyPsiUtils.collectStubChildren(this, getStub(), PyElementTypes.FROM_IMPORT_STATEMENT, PyFromImportStatement.class);
  }

  @Override
  public List<String> getDunderAll() {
    final StubElement stubElement = getStub();
    if (stubElement instanceof PyFileStub) {
      return ((PyFileStub)stubElement).getDunderAll();
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
    private final Map<String, List<String>> myDunderLike = new HashMap<String, List<String>>();

    @Override
    public void visitPyFile(PyFile node) {
      if (node.getText().contains(PyNames.ALL)) {
        super.visitPyFile(node);
      }
    }

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
      if (expression instanceof PyReferenceExpression && !((PyReferenceExpression)expression).isQualified()) {
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

  @Override
  public boolean hasImportFromFuture(FutureFeature feature) {
    final StubElement stub = getStub();
    if (stub instanceof PyFileStub) {
      return ((PyFileStub)stub).getFutureFeatures().get(feature.ordinal());
    }
    Boolean enabled = myFutureFeatures.get(feature);
    if (enabled == null) {
      enabled = calculateImportFromFuture(feature);
      myFutureFeatures.put(feature, enabled);
      // NOTE: ^^^ not synchronized. if two threads will try to modify this, both can only be expected to set the same value.
    }
    return enabled;
  }

  @Override
  public String getDeprecationMessage() {
    final StubElement stub = getStub();
    if (stub instanceof PyFileStub) {
      return ((PyFileStub)stub).getDeprecationMessage();
    }
    return extractDeprecationMessage();
  }

  @Override
  public List<PyImportStatementBase> getImportBlock() {
    List<PyImportStatementBase> result = new ArrayList<PyImportStatementBase>();
    ASTNode firstImport = getNode().getFirstChildNode();
    while (firstImport != null && !isImport(firstImport, false)) {
      firstImport = firstImport.getTreeNext();
    }
    if (firstImport != null) {
      result.add(firstImport.getPsi(PyImportStatementBase.class));
      ASTNode lastImport = firstImport.getTreeNext();
      while (lastImport != null && isImport(lastImport.getTreeNext(), true)) {
        if (isImport(lastImport, false)) {
          result.add(lastImport.getPsi(PyImportStatementBase.class));
        }
        lastImport = lastImport.getTreeNext();
      }
    }
    return result;
  }

  public String extractDeprecationMessage() {
    if (canHaveDeprecationMessage(getText())) {
      return PyFunctionImpl.extractDeprecationMessage(getStatements());
    }
    else {
      return null;
    }
  }

  private static boolean canHaveDeprecationMessage(String text) {
    return text.contains(PyNames.DEPRECATION_WARNING) || text.contains(PyNames.PENDING_DEPRECATION_WARNING);
  }

  public boolean calculateImportFromFuture(FutureFeature feature) {
    if (getText().contains(feature.toString())) {
      final List<PyFromImportStatement> fromImports = getFromImports();
      for (PyFromImportStatement fromImport : fromImports) {
        if (fromImport.isFromFuture()) {
          final PyImportElement[] pyImportElements = fromImport.getImportElements();
          for (PyImportElement element : pyImportElements) {
            final QualifiedName qName = element.getImportedQName();
            if (qName != null && qName.matches(feature.toString())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }


  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (myType == null) myType = new PyModuleType(this);
    return myType;
  }

  @Nullable
  @Override
  public String getDocStringValue() {
    return DocStringUtil.getDocStringValue(this);
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return DocStringUtil.getStructuredDocString(this);
  }

  @Nullable
  @Override
  public PyStringLiteralExpression getDocStringExpression() {
    return DocStringUtil.findDocStringExpression(this);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
    myDunderAllCalculated = false;
    myFutureFeatures.clear(); // probably no need to synchronize
    myExportedNameCache.clear();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    String path = getVirtualFile().getPath();
    super.delete();
    PyUtil.deletePycFiles(path);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    String path = getVirtualFile().getPath();
    final PsiElement newElement = super.setName(name);
    PyUtil.deletePycFiles(path);
    return newElement;
  }

  private static class ArrayListThreadLocal extends ThreadLocal<List<String>> {
    @Override
    protected List<String> initialValue() {
      return new ArrayList<String>();
    }
  }

  public static boolean isImport(ASTNode node, boolean orWhitespace) {
    if (node == null) return false;
    IElementType elementType = node.getElementType();
    if (orWhitespace && elementType == TokenType.WHITE_SPACE) {
      return true;
    }
    return elementType == PyElementTypes.IMPORT_STATEMENT || elementType == PyElementTypes.FROM_IMPORT_STATEMENT;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return getModuleName(PyFileImpl.this);
      }

      @Override
      public String getLocationString() {
        final String name = getLocationName();
        return name != null ? "(" + name + ")" : null;
      }

      @Override
      public Icon getIcon(final boolean open) {
        if (PyUtil.isPackage(PyFileImpl.this)) {
          return AllIcons.Modules.SourceFolder;
        }
        return PyFileImpl.this.getIcon(0);
      }

      @NotNull
      private String getModuleName(@NotNull PyFile file) {
        if (PyUtil.isPackage(file)) {
          final PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            return dir.getName();
          }
        }
        return FileUtil.getNameWithoutExtension(file.getName());
      }

      @Nullable
      private String getLocationName() {
        final QualifiedName name = QualifiedNameFinder.findShortestImportableQName(PyFileImpl.this);
        if (name != null) {
          final QualifiedName prefix = name.removeTail(1);
          if (prefix.getComponentCount() > 0) {
            return prefix.toString();
          }
        }
        final String relativePath = getRelativeContainerPath();
        if (relativePath != null) {
          return relativePath;
        }
        final PsiDirectory psiDirectory = getParent();
        if (psiDirectory != null) {
          return psiDirectory.getVirtualFile().getPresentableUrl();
        }
        return null;
      }

      @Nullable
      private String getRelativeContainerPath() {
        final PsiDirectory psiDirectory = getParent();
        if (psiDirectory != null) {
          final VirtualFile virtualFile = getVirtualFile();
          if (virtualFile != null) {
            final VirtualFile root = ProjectFileIndex.SERVICE.getInstance(getProject()).getContentRootForFile(virtualFile);
            if (root != null) {
              final VirtualFile parent = virtualFile.getParent();
              final VirtualFile rootParent = root.getParent();
              if (rootParent != null && parent != null) {
                return VfsUtilCore.getRelativePath(parent, rootParent, File.separatorChar);
              }
            }
          }
        }
        return null;
      }
    };
  }
}
