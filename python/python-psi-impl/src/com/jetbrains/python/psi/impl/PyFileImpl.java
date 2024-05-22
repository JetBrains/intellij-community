// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Maps;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.impl.stubs.PyVersionSpecificStubBaseKt;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.Function;

public class PyFileImpl extends PsiFileBase implements PyFile, PyExpression {
  @Nullable protected volatile PyType myType;

  //private volatile Boolean myAbsoluteImportEnabled;
  private final Map<FutureFeature, Boolean> myFutureFeatures;
  @Nullable private volatile List<String> myDunderAll;
  private volatile boolean myDunderAllCalculated;
  @NotNull private volatile SoftReference<ExportedNameCache> myExportedNameCache = new SoftReference<>(null);
  @NotNull private final PsiModificationTracker myModificationTracker;

  private final class ExportedNameCache {
    private final List<String> myNameDefinerNegativeCache = new ArrayList<>();
    private long myNameDefinerOOCBModCount = -1;
    private final long myModificationStamp;
    private final Map<String, List<PsiNamedElement>> myNamedElements = Maps.newHashMap();
    private final List<PyImportedNameDefiner> myImportedNameDefiners = new ArrayList<>();

    private ExportedNameCache(long modificationStamp) {
      myModificationStamp = modificationStamp;

      final StubElement<?> stub = getStub();
      LanguageLevel languageLevel = PyiUtil.getOriginalLanguageLevel(PyFileImpl.this);
      processDeclarations(PyFileImpl.this, stub, languageLevel, element -> {
        if (element instanceof PsiNamedElement namedElement &&
            !(element instanceof PyKeywordArgument) &&
            !(stub == null && element.getParent() instanceof PyImportElement)) {
          myNamedElements.computeIfAbsent(namedElement.getName(), __ -> new ArrayList<>()).add(namedElement);
        }
        if (element instanceof PyImportedNameDefiner) {
          myImportedNameDefiners.add((PyImportedNameDefiner)element);
        }
        if (element instanceof PyFromImportStatement fromImportStatement) {
          final PyStarImportElement starImportElement = fromImportStatement.getStarImportElement();
          if (starImportElement != null) {
            myImportedNameDefiners.add(starImportElement);
          }
          else {
            Collections.addAll(myImportedNameDefiners, fromImportStatement.getImportElements());
          }
        }
        else if (element instanceof PyImportStatement importStatement) {
          Collections.addAll(myImportedNameDefiners, importStatement.getImportElements());
        }
        return true;
      });
      TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(getProject());
      for (List<PsiNamedElement> elements : myNamedElements.values()) {
        prioritizeNameRedefinitions(elements, typeEvalContext);
      }

      Collections.reverse(myImportedNameDefiners);
    }

    private static boolean processDeclarations(@NotNull PsiElement element,
                                               @Nullable StubElement<?> stub,
                                               @NotNull LanguageLevel languageLevel,
                                               @NotNull Processor<? super PsiElement> processor) {
      for (PsiElement child : collectAllChildren(element, stub, languageLevel)) {
        if (!processor.process(child)) {
          return false;
        }
        if (child instanceof PyExceptPart part) {
          if (!processDeclarations(part, part.getStub(), languageLevel, processor)) {
            return false;
          }
        }
      }
      return true;
    }

    @NotNull
    private List<RatedResolveResult> multiResolve(@NotNull String name) {
      synchronized (myNameDefinerNegativeCache) {
        final long modCount = myModificationTracker.getModificationCount();
        if (modCount != myNameDefinerOOCBModCount) {
          myNameDefinerNegativeCache.clear();
          myNameDefinerOOCBModCount = modCount;
        }
        else {
          if (myNameDefinerNegativeCache.contains(name)) {
            return Collections.emptyList();
          }
        }
      }

      final PyResolveProcessor processor = new PyResolveProcessor(name);
      boolean stopped = false;
      if (myNamedElements.containsKey(name)) {
        for (PsiNamedElement element : myNamedElements.get(name)) {
          if (!processor.execute(element, ResolveState.initial())) {
            stopped = true;
            break;
          }
        }
      }
      if (!stopped) {
        for (PyImportedNameDefiner definer : myImportedNameDefiners) {
          if (!processor.execute(definer, ResolveState.initial())) {
            break;
          }
        }
      }
      final Map<PsiElement, PyImportedNameDefiner> results = processor.getResults();
      if (!results.isEmpty()) {
        final ResolveResultList resultList = new ResolveResultList();
        final TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(getProject());
        for (Map.Entry<PsiElement, PyImportedNameDefiner> entry : results.entrySet()) {
          final PsiElement element = entry.getKey();
          final PyImportedNameDefiner definer = entry.getValue();
          if (element != null) {
            final int elementRate = PyReferenceImpl.getRate(element, typeEvalContext);
            if (definer != null) {
              resultList.add(new ImportedResolveResult(element, elementRate, definer));
            }
            else {
              resultList.poke(element, elementRate);
            }
          }
        }

        return resultList;
      }

      synchronized (myNameDefinerNegativeCache) {
        myNameDefinerNegativeCache.add(name);
      }
      return Collections.emptyList();
    }

    public long getModificationStamp() {
      return myModificationStamp;
    }
  }

  protected void prioritizeNameRedefinitions(@NotNull List<PsiNamedElement> definitions, @NotNull TypeEvalContext typeEvalContext) {
    // Group overloads with their respective implementations
    List<List<PsiNamedElement>> grouped = StreamEx.of(definitions)
      .groupRuns((e1, e2) -> PyiUtil.isOverload(e1, typeEvalContext) && e2 instanceof PyFunction)
      .toList();
    definitions.clear();
    StreamEx.ofReversed(grouped)
      .flatCollection(Function.identity())
      .into(definitions);
  }

  public PyFileImpl(FileViewProvider viewProvider) {
    this(viewProvider, PythonLanguage.getInstance());
  }

  public PyFileImpl(FileViewProvider viewProvider, Language language) {
    super(viewProvider, language);
    myFutureFeatures = new HashMap<>();
    myModificationTracker = PsiModificationTracker.getInstance(getProject());
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "PyFile:" + getName();
  }

  @Override
  public PyFunction findTopLevelFunction(@NotNull String name) {
    return findByName(name, getTopLevelFunctions());
  }

  @Override
  public PyClass findTopLevelClass(@NotNull String name) {
    return findByName(name, getTopLevelClasses());
  }

  @Override
  public @Nullable PyTargetExpression findTopLevelAttribute(@NotNull String name) {
    return findByName(name, getTopLevelAttributes());
  }

  @Override
  public @NotNull List<PyTypeAliasStatement> getTypeAliasStatements() {
    return collectChildren(PyTypeAliasStatement.class);
  }

  @Override
  @Nullable
  public PyTypeAliasStatement findTypeAliasStatement(@NotNull String name) {
    return findByName(name, getTypeAliasStatements());
  }

  @Nullable
  private static <T extends PsiNamedElement> T findByName(@NotNull String name, @NotNull List<T> namedElements) {
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
      PsiFile originalPythonFile = myOriginalFile;
      // myOriginalFile could be an instance of base language
      // see PostfixLiveTemplate#copyFile
      if (myOriginalFile.getViewProvider() instanceof TemplateLanguageFileViewProvider) {
        originalPythonFile = myOriginalFile.getViewProvider().getPsi(PythonLanguage.getInstance());
      }
      if (originalPythonFile instanceof PyFile) {
        return ((PyFile)originalPythonFile).getLanguageLevel();
      }
    }
    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null) {
      virtualFile = getViewProvider().getVirtualFile();
    }
    return PythonLanguageLevelPusher.getLanguageLevelForVirtualFile(getProject(), virtualFile);
  }

  @Override
  public Icon getIcon(int flags) {
    return PythonFileType.INSTANCE.getIcon();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (isAcceptedFor(visitor.getClass())) {
      if (visitor instanceof PyElementVisitor pyVisitor) {
        pyVisitor.visitPyFile(this);
      }
      else if (visitor instanceof PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyFile(this);
      }
      else {
        super.accept(visitor);
      }
    }
  }
  //
  //@Override
  //public PsiElement getNavigationElement() {
  //  final PsiElement element = PyiUtil.getOriginalElement(this);
  //  return element != null ? element : super.getNavigationElement();
  //}

  @Override
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
    final List<String> remainingDunderAll = dunderAll == null ? null : new ArrayList<>(dunderAll);
    PsiScopeProcessor wrapper = new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (!super.execute(element, state)) return false;
        if (remainingDunderAll != null && element instanceof PyElement) {
          remainingDunderAll.remove(((PyElement)element).getName());
        }
        return true;
      }
    };

    Set<PyFile> pyFiles = resolveState.get(PROCESSED_FILES);
    if (pyFiles == null) {
      pyFiles = new HashSet<>();
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
  @NotNull
  public List<PyClass> getTopLevelClasses() {
    return collectChildren(PyClass.class);
  }

  @NotNull
  @Override
  public List<PyFunction> getTopLevelFunctions() {
    return collectChildren(PyFunction.class);
  }

  @Override
  public List<PyTargetExpression> getTopLevelAttributes() {
    return collectChildren(PyTargetExpression.class);
  }

  @Override
  @Nullable
  public PsiElement findExportedName(final String name) {
    final List<RatedResolveResult> results = multiResolveName(name);
    final List<PsiElement> elements = new ArrayList<>();
    for (RatedResolveResult result : results) {
      final PsiElement element = result.getElement();
      final ImportedResolveResult importedResult = PyUtil.as(result, ImportedResolveResult.class);
      if (importedResult != null) {
        final PyImportedNameDefiner definer = importedResult.getDefiner();
        if (definer != null) {
          elements.add(definer);
        }
      }
      else if (element != null && element.getContainingFile() == this) {
        elements.add(element);
      }
    }
    final PsiElement element = elements.isEmpty() ? null : elements.get(elements.size() - 1);
    if (element != null && !element.isValid()) {
      throw new PsiInvalidElementAccessException(element);
    }
    return element;
  }

  @NotNull
  @Override
  public List<RatedResolveResult> multiResolveName(@NotNull final String name) {
    return multiResolveName(name, true);
  }

  @NotNull
  @Override
  public List<RatedResolveResult> multiResolveName(@NotNull String name, boolean exported) {
    final List<RatedResolveResult> results = RecursionManager.doPreventingRecursion(this, false,
                                                                                    () -> getExportedNameCache().multiResolve(name));
    if (results != null && !results.isEmpty()) {
      return results;
    }
    final List<String> allNames = getDunderAll();
    if (allNames != null && !name.contains(".") && allNames.contains(name)) {
      final ResolveResultList allFallbackResults = new ResolveResultList();

      PyResolveImportUtil
        .resolveModuleAt(QualifiedName.fromComponents(name), getContainingDirectory(), PyResolveImportUtil.fromFoothold(this))
        .forEach(module -> allFallbackResults.poke(module, RatedResolveResult.RATE_LOW));

      final PsiElement allElement = findExportedName(PyNames.ALL);
      allFallbackResults.poke(allElement, RatedResolveResult.RATE_LOW);

      return allFallbackResults;
    }
    return Collections.emptyList();
  }

  private ExportedNameCache getExportedNameCache() {
    ExportedNameCache cache = myExportedNameCache.get();
    final long modificationStamp = getModificationStamp();
    if (cache != null && modificationStamp != cache.getModificationStamp()) {
      myExportedNameCache.clear();
      cache = null;
    }
    if (cache == null) {
      cache = new ExportedNameCache(modificationStamp);
      myExportedNameCache = new SoftReference<>(cache);
    }
    return cache;
  }

  @Override
  @Nullable
  public PsiElement getElementNamed(final String name) {
    final List<RatedResolveResult> results = multiResolveName(name);
    final List<PsiElement> elements = PyUtil.filterTopPriorityResults(results.toArray(ResolveResult.EMPTY_ARRAY));
    final PsiElement element = elements.isEmpty() ? null : elements.get(elements.size() - 1);
    if (element != null) {
      if (!element.isValid()) {
        throw new PsiInvalidElementAccessException(element);
      }
      return element;
    }
    return null;
  }

  @Override
  @NotNull
  public Iterable<PyElement> iterateNames() {
    final List<PyElement> result = new ArrayList<>();
    final VariantsProcessor processor = new VariantsProcessor(this) {
      @Override
      protected void addElement(@NotNull String name, @NotNull PsiElement element) {
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
  @NotNull
  public List<PyImportElement> getImportTargets() {
    final List<PyImportElement> ret = new ArrayList<>();
    final List<PyImportStatement> imports = collectChildren(PyImportStatement.class);
    for (PyImportStatement one : imports) {
      ContainerUtil.addAll(ret, one.getImportElements());
    }
    return ret;
  }

  @Override
  @NotNull
  public List<PyFromImportStatement> getFromImports() {
    return collectChildren(PyFromImportStatement.class);
  }

  @Nullable
  @Override
  public List<String> getDunderAll() {
    return withGreenStubOrAst(
      PyFileStub.class,
      stub -> stub.getDunderAll(),
      ast -> {
        if (!myDunderAllCalculated) {
          final List<String> dunderAll = calculateDunderAll();
          myDunderAll = dunderAll == null ? null : Collections.unmodifiableList(dunderAll);
          myDunderAllCalculated = true;
        }
        return myDunderAll;
      }
    );
  }

  @Nullable
  public List<String> calculateDunderAll() {
    final DunderAllBuilder builder = new DunderAllBuilder();
    accept(builder);
    return builder.result();
  }

  private static class DunderAllBuilder extends PyRecursiveElementVisitor {

    @NotNull
    private final List<String> myResult = new ArrayList<>();
    private boolean myDynamic = false;
    private boolean myFoundDunderAll = false;

    // hashlib builds __all__ by concatenating multiple lists of strings, and we want to understand this
    @NotNull
    private final Map<String, List<String>> myDunderLike = new HashMap<>();

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      if (node.getText().contains(PyNames.ALL)) {
        super.visitPyFile(node);
      }
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      if (myDynamic) return;

      if (PyNames.ALL.equals(node.getName())) {
        myFoundDunderAll = true;
        final PyExpression value = node.findAssignedValue();
        if (value instanceof PyBinaryExpression binaryExpression) {
          if (binaryExpression.isOperator("+")) {
            processSubList(getStringListFromValue(binaryExpression.getLeftExpression()));
            processSubList(getStringListFromValue(binaryExpression.getRightExpression()));
          }
          else {
            myDynamic = true;
          }
        }
        else {
          processSubList(getStringListFromValue(value));
        }
      }

      if (!myFoundDunderAll) {
        final List<String> names = getStringListFromValue(node.findAssignedValue());
        if (names != null) {
          myDunderLike.put(node.getName(), names);
        }
      }
    }

    @Override
    public void visitPyAugAssignmentStatement(@NotNull PyAugAssignmentStatement node) {
      if (myDynamic || !myFoundDunderAll) return;

      if (PyNames.ALL.equals(node.getTarget().getName())) {
        processSubList(getStringListFromValue(node.getValue()));
      }
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      if (myDynamic || !myFoundDunderAll) return;

      final PyExpression callee = node.getCallee();
      if (callee instanceof PyQualifiedExpression) {
        final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
        if (qualifier != null && PyNames.ALL.equals(qualifier.getText())) {
          final String calleeName = callee.getName();
          if (PyNames.APPEND.equals(calleeName)) {
            final PyStringLiteralExpression argument = node.getArgument(0, PyStringLiteralExpression.class);
            if (argument != null) {
              myResult.add(argument.getStringValue());
              return;
            }
          }
          else if (PyNames.EXTEND.equals(calleeName)) {
            final PyExpression argument = node.getArgument(0, PyExpression.class);
            processSubList(getStringListFromValue(argument));
            return;
          }

          myDynamic = true;
        }
      }
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
    }

    @Nullable
    List<String> result() {
      return myDynamic || !myFoundDunderAll ? null : myResult;
    }

    private void processSubList(@Nullable List<String> list) {
      if (list == null) {
        myDynamic = true;
      }
      else {
        myResult.addAll(list);
      }
    }

    @Nullable
    private List<String> getStringListFromValue(@Nullable PyExpression expression) {
      if (expression instanceof PyReferenceExpression && !((PyReferenceExpression)expression).isQualified()) {
        return myDunderLike.get(((PyReferenceExpression)expression).getReferencedName());
      }
      return PyUtil.strListValue(expression);
    }
  }

  @Override
  public boolean hasImportFromFuture(FutureFeature feature) {
    return withGreenStubOrAst(
      PyFileStub.class,
      stub -> stub.getFutureFeatures().get(feature.ordinal()),
      ast -> {
        Boolean enabled = myFutureFeatures.get(feature);
        if (enabled == null) {
          enabled = calculateImportFromFuture(feature);
          myFutureFeatures.put(feature, enabled);
          // NOTE: ^^^ not synchronized. if two threads will try to modify this, both can only be expected to set the same value.
        }
        return enabled;
      }
    );
  }

  @Override
  public String getDeprecationMessage() {
    return withGreenStubOrAst(
      PyFileStub.class,
      stub -> stub.getDeprecationMessage(),
      ast -> extractDeprecationMessage()
    );
  }

  @Override
  public List<PyImportStatementBase> getImportBlock() {
    final List<PyImportStatementBase> result = new ArrayList<>();
    final PsiElement firstChild = getFirstChild();
    PsiElement currentStatement;
    if (firstChild instanceof PyImportStatementBase) {
      currentStatement = firstChild;
    }
    else {
      currentStatement = PsiTreeUtil.getNextSiblingOfType(firstChild, PyImportStatementBase.class);
    }
    if (currentStatement != null) {
      // skip imports from future before module level dunders
      final List<PyImportStatementBase> fromFuture = new ArrayList<>();
      while (currentStatement instanceof PyFromImportStatement && ((PyFromImportStatement)currentStatement).isFromFuture()) {
        fromFuture.add((PyImportStatementBase)currentStatement);
        currentStatement = PyPsiUtils.getNextNonCommentSibling(currentStatement, true);
      }

      // skip module level dunders
      boolean hasModuleLevelDunders = false;
      while (PyUtilCore.isAssignmentToModuleLevelDunderName(currentStatement)) {
        hasModuleLevelDunders = true;
        currentStatement = PyPsiUtils.getNextNonCommentSibling(currentStatement, true);
      }

      // if there is an import from future and a module level-dunder between it and other imports,
      // this import is not considered a part of the import block to avoid problems with "Optimize imports" and foldings
      if (!hasModuleLevelDunders) {
        result.addAll(fromFuture);
      }
      // collect imports
      while (currentStatement instanceof PyImportStatementBase) {
        result.add((PyImportStatementBase)currentStatement);
        currentStatement = PyPsiUtils.getNextNonCommentSibling(currentStatement, true);
      }
    }
    return result;
  }

  public String extractDeprecationMessage() {
    if (canHaveDeprecationMessage(getText())) {
      return PyFunction.extractDeprecationMessage(getStatements());
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


  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (myType == null) myType = new PyModuleType(this);
    return myType;
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return DocStringUtil.getStructuredDocString(this);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
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
          return AllIcons.Nodes.Package;
        }
        return PyFileImpl.this.getIcon(0);
      }

      @NotNull
      private static String getModuleName(@NotNull PyFile file) {
        if (PyUtil.isPackage(file)) {
          final PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            return dir.getName();
          }
        }
        return FileUtilRt.getNameWithoutExtension(file.getName());
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
            final VirtualFile root = ProjectFileIndex.getInstance(getProject()).getContentRootForFile(virtualFile);
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

  @NotNull
  private <T extends PyElement> List<T> collectChildren(Class<T> type) {
    @Nullable StubElement<?> stub = getGreenStub();
    @NotNull LanguageLevel languageLevel = PyiUtil.getOriginalLanguageLevel(this);
    final List<T> result = new ArrayList<>();
    if (stub != null) {
      for (StubElement<?> child : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
        PsiElement childPsi = child.getPsi();
        if (type.isInstance(childPsi)) {
          result.add(type.cast(childPsi));
        }
      }
    }
    else {
      acceptChildren(new PyVersionAwareTopLevelElementVisitor(languageLevel) {
        @Override
        protected void checkAddElement(PsiElement node) {
          if (type.isInstance(node)) {
            result.add(type.cast(node));
          }
        }

        @Override
        public void visitPyStatement(@NotNull PyStatement node) {
          if (PyStatement.class.isAssignableFrom(type) && !(node instanceof PyCompoundStatement)) {
            checkAddElement(node);
            return;
          }
          super.visitPyStatement(node);
        }
      });
    }
    return result;
  }

  @NotNull
  private static List<PsiElement> collectAllChildren(@NotNull PsiElement element,
                                                     @Nullable StubElement<?> stub,
                                                     @NotNull LanguageLevel languageLevel) {
    List<PsiElement> result = new ArrayList<>();
    if (stub != null) {
      for (StubElement<?> child : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
        result.add(child.getPsi());
      }
    }
    else {
      element.acceptChildren(new PyVersionAwareTopLevelElementVisitor(languageLevel) {
        @Override
        protected void checkAddElement(PsiElement node) {
          result.add(node);
        }
      });
    }
    return result;
  }
}
