// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFileType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Adds completion variants for Python classes, functions and variables.
 */
public final class PyClassNameCompletionContributor extends PyImportableNameCompletionContributor {
  // See https://plugins.jetbrains.com/plugin/18465-sputnik
  private static final boolean TRACING_WITH_SPUTNIK_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(PyClassNameCompletionContributor.class);
  private static final int NAME_TOO_SHORT_FOR_BASIC_COMPLETION_THRESHOLD = 5;
  // See PY-73964, IJPL-265
  private static final boolean RECURSIVE_INDEX_ACCESS_ALLOWED = false;

  public PyClassNameCompletionContributor() {
    if (TRACING_WITH_SPUTNIK_ENABLED) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("\01hr('Importable names completion')");
    }
  }

  @Override
  protected void doFillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    result.restartCompletionWhenNothingMatches();
    var remainingResults = result.runRemainingContributors(parameters, true);

    if (parameters.isExtendedCompletion() || remainingResults.isEmpty() || containsOnlyElementUnderTheCaret(remainingResults, parameters)) {
      fillCompletionVariantsImpl(parameters, result);
    }
  }

  private static boolean containsOnlyElementUnderTheCaret(@NotNull Set<CompletionResult> remainingResults,
                                                          @NotNull CompletionParameters parameters) {
    PsiElement position = parameters.getOriginalPosition();
    if (remainingResults.size() == 1 && position != null) {
      var lookup = ContainerUtil.getFirstItem(remainingResults);
      return lookup.getLookupElement().getLookupString().equals(position.getText());
    }
    return false;
  }

  private void fillCompletionVariantsImpl(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    boolean isExtendedCompletion = parameters.isExtendedCompletion();
    if (!PyCodeInsightSettings.getInstance().INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION && !isExtendedCompletion) {
      return;
    }
    PsiFile originalFile = parameters.getOriginalFile();
    PsiElement position = parameters.getPosition();
    PyReferenceExpression refExpr = as(position.getParent(), PyReferenceExpression.class);
    PyTargetExpression targetExpr = as(position.getParent(), PyTargetExpression.class);
    PsiElement originalPosition = parameters.getOriginalPosition();
    // In cases like `fo<caret>o = 42` the target expression gets split by the trailing space at the end of DUMMY_IDENTIFIER as
    // `foIntellijIdeaRulezzz o = 42`, so in the copied file we're still completing inside a standalone reference expression.
    boolean originallyInsideTarget = originalPosition != null && originalPosition.getParent() instanceof PyTargetExpression;
    boolean insideUnqualifiedReference = refExpr != null && !refExpr.isQualified() && !originallyInsideTarget;
    boolean insidePattern = targetExpr != null && position.getParent().getParent() instanceof PyCapturePattern;
    boolean insideStringLiteralInExtendedCompletion = position instanceof PyStringElement && isExtendedCompletion;
    if (!(insideUnqualifiedReference || insidePattern || insideStringLiteralInExtendedCompletion)) {
      return;
    }

    // Directly inside the class body scope, it's rarely needed to have expression statements
    // TODO apply the same logic for completion of importable module and package names
    if (refExpr != null &&
        (isDirectlyInsideClassBody(refExpr) || isInsideErrorElement(refExpr))) {
      return;
    }
    // TODO Use another method to collect already visible names
    // Candidates: PyExtractMethodValidator, IntroduceValidator.isDefinedInScope
    PsiReference refUnderCaret = refExpr != null ? refExpr.getReference() :
                                 targetExpr != null ? targetExpr.getReference() :
                                 null;
    Set<String> namesInScope = refUnderCaret == null ? Collections.emptySet() : StreamEx.of(refUnderCaret.getVariants())
      .select(LookupElement.class)
      .map(LookupElement::getLookupString)
      .toSet();
    Project project = originalFile.getProject();
    TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(project, originalFile);
    int maxVariants = Registry.intValue("ide.completion.variant.limit");
    Counters counters = new Counters();
    StubIndex stubIndex = StubIndex.getInstance();
    TimeoutUtil.run(() -> {
      GlobalSearchScope scope = createScope(originalFile);
      Set<QualifiedName> alreadySuggested = new HashSet<>();
      forEachPublicNameFromIndex(scope, elementName -> {
        ProgressManager.checkCanceled();
        counters.scannedNames++;
        if (elementName.length() < NAME_TOO_SHORT_FOR_BASIC_COMPLETION_THRESHOLD && !isExtendedCompletion) {
          counters.tooShortNames++;
          return true;
        }
        if (!result.getPrefixMatcher().isStartMatch(elementName)) return true;
        return stubIndex.processElements(PyExportedModuleAttributeIndex.KEY, elementName, project, scope, PyElement.class, exported -> {
          ProgressManager.checkCanceled();
          String name = exported.getName();
          if (name == null || namesInScope.contains(name)) return true;
          QualifiedName fqn = getFullyQualifiedName(exported);
          if (!isApplicableInInsertionContext(exported, fqn, position, typeEvalContext)) {
            counters.notApplicableInContextNames++;
            return true;
          }
          if (alreadySuggested.add(fqn)) {
            if (isPrivateDefinition(fqn, exported, originalFile)) {
              counters.privateNames++;
              return true;
            }
            LookupElementBuilder lookupElement = LookupElementBuilder
              .createWithSmartPointer(name, exported)
              .withIcon(exported.getIcon(0))
              .withExpensiveRenderer(new LookupElementRenderer<>() {
                @Override
                public void renderElement(LookupElement element, LookupElementPresentation presentation) {
                  presentation.setItemText(element.getLookupString());
                  presentation.setIcon(exported.getIcon(0));
                  QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(exported, originalFile);
                  if (importPath == null) return;
                  presentation.setTypeText(importPath.toString());
                }
              })
              .withInsertHandler(getInsertHandler(exported, position, typeEvalContext));
            result.addElement(PrioritizedLookupElement.withPriority(lookupElement, PythonCompletionWeigher.NOT_IMPORTED_MODULE_WEIGHT));
            counters.totalVariants++;
            if (counters.totalVariants >= maxVariants) return false;
          }
          return true;
        });
      });
    }, duration -> {
      LOG.debug(counters + " computed for prefix '" + result.getPrefixMatcher().getPrefix() + "' in " + duration + " ms");
      if (TRACING_WITH_SPUTNIK_ENABLED) {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.printf("\1h('Importable names completion','%d')%n", (duration / 10) * 10);
        //noinspection UseOfSystemOutOrSystemErr
        System.out.printf("\1Hi(%d)%n", duration);
      }
    });
  }

  private static void forEachPublicNameFromIndex(@NotNull GlobalSearchScope scope, @NotNull Processor<String> processor) {
    StubIndex stubIndex = StubIndex.getInstance();
    if (!RECURSIVE_INDEX_ACCESS_ALLOWED) {
      Project project = Objects.requireNonNull(scope.getProject());
      CachedValuesManager manager = CachedValuesManager.getManager(project);

      var cachedAllNames = manager.getCachedValue(project, () -> {
        StubIndex index = StubIndex.getInstance();
        Collection<String> keys = index.getAllKeys(PyExportedModuleAttributeIndex.KEY, project);
        ModificationTracker modificationTracker = index.getPerFileElementTypeModificationTracker(PyFileElementType.INSTANCE);
        return CachedValueProvider.Result.create(keys, modificationTracker);
      });

      for (String allKey : cachedAllNames) {
        if (!processor.process(allKey)) {
          return;
        }
      }
    }
    else {
      stubIndex.processAllKeys(PyExportedModuleAttributeIndex.KEY, processor, scope);
    }
  }

  private static boolean isApplicableInInsertionContext(@NotNull PyElement definition,
                                                        @NotNull QualifiedName fqn, @NotNull PsiElement position,
                                                        @NotNull TypeEvalContext context) {
    if (PyTypingTypeProvider.isInsideTypeHint(position, context)) {
      // Not all names from typing.py are defined as classes
      return (
        definition instanceof PyClass ||
        definition instanceof PyTypeAliasStatement ||
        ArrayUtil.contains(fqn.getFirstComponent(), "typing", "typing_extensions")
      );
    }
    if (PsiTreeUtil.getParentOfType(position, PyPattern.class, false) != null) {
      return definition instanceof PyClass;
    }
    return true;
  }

  private static boolean isInsideErrorElement(@NotNull PyReferenceExpression referenceExpression) {
    return PsiTreeUtil.getParentOfType(referenceExpression, PsiErrorElement.class) != null;
  }

  private static boolean isDirectlyInsideClassBody(@NotNull PyReferenceExpression referenceExpression) {
    return referenceExpression.getParent() instanceof PyExpressionStatement statement &&
           ScopeUtil.getScopeOwner(statement) instanceof PyClass;
  }

  private static @NotNull QualifiedName getFullyQualifiedName(@NotNull PyElement exported) {
    String shortName = StringUtil.notNullize(exported.getName());
    String qualifiedName = exported instanceof PyQualifiedNameOwner qNameOwner ? qNameOwner.getQualifiedName() : null;
    return QualifiedName.fromDottedString(qualifiedName != null ? qualifiedName : shortName);
  }

  private static boolean isPrivateDefinition(@NotNull QualifiedName fqn, @NotNull PyElement exported, PsiFile originalFile) {
    if (containsPrivateComponents(fqn)) {
      QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(exported, originalFile);
      return importPath != null && containsPrivateComponents(importPath);
    }
    return false;
  }

  private static boolean containsPrivateComponents(@NotNull QualifiedName fqn) {
    return ContainerUtil.exists(fqn.getComponents(), c -> c.startsWith("_"));
  }

  private static @NotNull GlobalSearchScope createScope(@NotNull PsiFile originalFile) {
    class HavingLegalImportPathScope extends QualifiedNameFinder.QualifiedNameBasedScope {
      private HavingLegalImportPathScope(@NotNull Project project) {
        super(project);
      }

      @Override
      protected boolean containsQualifiedNameInRoot(@NotNull VirtualFile root, @NotNull QualifiedName qName) {
        return ContainerUtil.all(qName.getComponents(), PyNames::isIdentifier) && !qName.equals(QualifiedName.fromComponents("__future__"));
      }
    }

    Project project = originalFile.getProject();
    var pyiStubsScope = GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.everythingScope(project), PyiFileType.INSTANCE);
    return PySearchUtilBase.defaultSuggestionScope(originalFile)
      .intersectWith(GlobalSearchScope.notScope(pyiStubsScope))
      .intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.fileScope(originalFile)))
      // Some types in typing.py are defined as functions, causing inserting them with parentheses. It's better to rely on typing.pyi.
      .intersectWith(GlobalSearchScope.notScope(fileScope("typing", originalFile, false)))
      .uniteWith(fileScope("typing", originalFile, true))
      .intersectWith(new HavingLegalImportPathScope(project));
  }

  private static @NotNull GlobalSearchScope fileScope(@NotNull String fqn, @NotNull PsiFile anchor, boolean pyiStub) {
    var context = pyiStub ? PyResolveImportUtil.fromFoothold(anchor) : PyResolveImportUtil.fromFoothold(anchor).copyWithoutStubs();
    var files = ContainerUtil.filterIsInstance(PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString(fqn), context),
                                               PsiFile.class);
    if (files.isEmpty()) return GlobalSearchScope.EMPTY_SCOPE;
    return GlobalSearchScope.filesWithLibrariesScope(anchor.getProject(), ContainerUtil.map(files, PsiFile::getVirtualFile));
  }

  private @NotNull InsertHandler<LookupElement> getInsertHandler(@NotNull PyElement exported,
                                                                 @NotNull PsiElement position,
                                                                 @NotNull TypeEvalContext typeEvalContext) {
    if (position.getParent() instanceof PyStringLiteralExpression) {
      if (isInsideAllInInitPy(position)) {
        return getImportingInsertHandler();
      }
      return getStringLiteralInsertHandler();
    }
    // Some names in typing are defined as functions, this rule needs to have priority
    else if (PyParameterizedTypeInsertHandler.isCompletingParameterizedType(exported, position, typeEvalContext)) {
      return getGenericTypeInsertHandler();
    }
    else if (exported instanceof PyFunction && !(position.getParent().getParent() instanceof PyDecorator)) {
      return getFunctionInsertHandler();
    }
    return getImportingInsertHandler();
  }

  private static boolean isInsideAllInInitPy(@NotNull PsiElement position) {
    PsiFile originalFile = position.getContainingFile();
    if (originalFile == null) {
      return false;
    }

    if (!PyNames.INIT_DOT_PY.equals(originalFile.getName())) {
      return false;
    }

    PyAssignmentStatement assignment =
      PsiTreeUtil.getParentOfType(position, PyAssignmentStatement.class);
    if (assignment == null) {
      return false;
    }

    PyExpression[] targets = assignment.getTargets();
    if (targets.length != 1) {
      return false;
    }

    PyExpression target = targets[0];
    if (!(target instanceof PyTargetExpression firstTarget)) {
      return false;
    }

    return PyNames.ALL.equals(firstTarget.getName());
  }

  private static class Counters {
    int scannedNames;
    int privateNames;
    int tooShortNames;
    int notApplicableInContextNames;
    int totalVariants;

    @Override
    public String toString() {
      return "Counters{" +
             "scannedNames=" + scannedNames +
             ", privateNames=" + privateNames +
             ", tooShortNames=" + tooShortNames +
             ", notApplicableInContextNames=" + notApplicableInContextNames +
             ", totalVariants=" + totalVariants +
             '}';
    }
  }
}
