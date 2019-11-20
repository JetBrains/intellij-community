// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 * User: dcheryasov
 */
public class PyUnresolvedReferencesInspection extends PyUnresolvedReferencesInspectionBase {
  private static final Key<Visitor> KEY = Key.create("PyUnresolvedReferencesInspection.Visitor");
  public static final Key<PyUnresolvedReferencesInspection> SHORT_NAME_KEY =
    Key.create(PyUnresolvedReferencesInspection.class.getSimpleName());

  public List<String> ignoredIdentifiers = new ArrayList<>();

  public static PyUnresolvedReferencesInspection getInstance(PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    return (PyUnresolvedReferencesInspection)inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    final Visitor visitor = new Visitor(holder, session, ignoredIdentifiers);
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final Visitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder holder) {
    final Visitor visitor = session.getUserData(KEY);
    assert visitor != null;
    if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
      visitor.highlightUnusedImports();
    }
    visitor.highlightImportsInsideGuards();
    session.putUserData(KEY, null);
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore references", ignoredIdentifiers);
    return form.getContentPanel();
  }

  public static class Visitor extends PyUnresolvedReferencesVisitor {
    private volatile Boolean myIsEnabled = null;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, List<String> ignoredIdentifiers) {
      super(holder, session, ignoredIdentifiers);
    }

    @NotNull
    @Override
    PyUnresolvedReferencesQuickFixBuilder quickFixBuilder() {
      return new PyUnresolvedReferencesQuickFixBuilderImpl();
    }

    @Override
    public boolean isEnabled(@NotNull PsiElement anchor) {
      if (myIsEnabled == null) {
        final boolean isPyCharm = PlatformUtils.isPyCharm();
        if (PySkeletonRefresher.isGeneratingSkeletons()) {
          myIsEnabled = false;
        }
        else if (isPyCharm) {
          myIsEnabled = PythonSdkUtil.findPythonSdk(anchor) != null || PythonRuntimeService.getInstance().isInScratchFile(anchor);
        }
        else {
          myIsEnabled = true;
        }
      }
      return myIsEnabled;
    }

    public void highlightUnusedImports() {
      final List<PyInspectionExtension> extensions = PyInspectionExtension.EP_NAME.getExtensionList();
      final List<PsiElement> unused = collectUnusedImportElements();
      for (PsiElement element : unused) {
        if (extensions.stream().anyMatch(extension -> extension.ignoreUnused(element, myTypeEvalContext))) {
          continue;
        }
        if (element.getTextLength() > 0) {
          PyUnresolvedReferencesQuickFixBuilder builder = quickFixBuilder();
          builder.addOptimizeImportsQuickFix();
          registerProblem(element, PyBundle.message("INSP.unused.import.statement"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, builder.build());
        }
      }
    }

    public void highlightImportsInsideGuards() {
      HashSet<PyImportedNameDefiner> usedImportsInsideImportGuards = Sets.newHashSet(getImportsInsideGuard());
      usedImportsInsideImportGuards.retainAll(getUsedImports());

      for (PyImportedNameDefiner definer : usedImportsInsideImportGuards) {

        PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
        if (importElement == null) {
          continue;
        }
        final PyTargetExpression asElement = importElement.getAsNameElement();
        final PyElement toHighlight = asElement != null ? asElement : importElement.getImportReferenceExpression();
        registerProblem(toHighlight,
                        PyBundle.message("INSP.try.except.import.error",
                                            importElement.getVisibleName()),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }

    private List<PsiElement> collectUnusedImportElements() {
      if (getAllImports().isEmpty()) {
        return Collections.emptyList();
      }
      // PY-1315 Unused imports inspection shouldn't work in python REPL console
      final PyImportedNameDefiner first = getAllImports().iterator().next();
      if (first.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(first)) {
        return Collections.emptyList();
      }
      List<PsiElement> result = new ArrayList<>();

      Set<PyImportedNameDefiner> unusedImports = new HashSet<>(getAllImports());
      unusedImports.removeAll(getUsedImports());

      // Remove those unsed, that are reported to be skipped by extension points
      final Set<PyImportedNameDefiner> unusedImportToSkip = new HashSet<>();
      for (final PyImportedNameDefiner unusedImport : unusedImports) {
        if (PyInspectionExtension.EP_NAME.getExtensionList().stream().anyMatch(o -> o.ignoreUnusedImports(unusedImport))) {
          unusedImportToSkip.add(unusedImport);
        }
      }

      unusedImports.removeAll(unusedImportToSkip);

      Set<String> usedImportNames = new HashSet<>();
      for (PyImportedNameDefiner usedImport : getUsedImports()) {
        for (PyElement e : usedImport.iterateNames()) {
          usedImportNames.add(e.getName());
        }
      }

      Set<PyImportStatementBase> unusedStatements = new HashSet<>();
      final PyUnresolvedReferencesInspection suppressableInspection = new PyUnresolvedReferencesInspection();
      QualifiedName packageQName = null;
      List<String> dunderAll = null;

      // TODO: Use strategies instead of pack of "continue"
      iterUnused:
      for (PyImportedNameDefiner unusedImport : unusedImports) {
        if (packageQName == null) {
          final PsiFile file = unusedImport.getContainingFile();
          if (file instanceof PyFile) {
            dunderAll = ((PyFile)file).getDunderAll();
          }
          if (file != null && PyUtil.isPackage(file)) {
            packageQName = QualifiedNameFinder.findShortestImportableQName(file);
          }
        }
        PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase.class);
        if (importStatement != null && !unusedStatements.contains(importStatement) && !getUsedImports().contains(unusedImport)) {
          if (suppressableInspection.isSuppressedFor(importStatement)) {
            continue;
          }
          // don't remove as unused imports in try/except statements
          if (PsiTreeUtil.getParentOfType(importStatement, PyTryExceptStatement.class) != null) {
            continue;
          }
          // Don't report conditional imports as unused
          if (PsiTreeUtil.getParentOfType(unusedImport, PyIfStatement.class) != null) {
            for (PyElement e : unusedImport.iterateNames()) {
              if (usedImportNames.contains(e.getName())) {
                continue iterUnused;
              }
            }
          }
          PsiFileSystemItem importedElement;
          if (unusedImport instanceof PyImportElement) {
            final PyImportElement importElement = (PyImportElement)unusedImport;
            final PsiElement element = importElement.resolve();
            if (element == null) {
              if (importElement.getImportedQName() != null) {
                //Mark import as unused even if it can't be resolved
                if (areAllImportsUnused(importStatement, unusedImports)) {
                  result.add(importStatement);
                }
                else {
                  result.add(importElement);
                }
              }
              continue;
            }
            if (dunderAll != null && dunderAll.contains(importElement.getVisibleName())) {
              continue;
            }
            importedElement = element.getContainingFile();
          }
          else {
            assert importStatement instanceof PyFromImportStatement;
            importedElement = ((PyFromImportStatement)importStatement).resolveImportSource();
            if (importedElement == null) {
              continue;
            }
          }
          if (packageQName != null && importedElement != null) {
            final QualifiedName importedQName = QualifiedNameFinder.findShortestImportableQName(importedElement);
            if (importedQName != null && importedQName.matchesPrefix(packageQName)) {
              continue;
            }
          }
          if (unusedImport instanceof PyStarImportElement || areAllImportsUnused(importStatement, unusedImports)) {
            unusedStatements.add(importStatement);
            result.add(importStatement);
          }
          else {
            result.add(unusedImport);
          }
        }
      }
      return result;
    }

    private static boolean areAllImportsUnused(PyImportStatementBase importStatement, Set<PyImportedNameDefiner> unusedImports) {
      final PyImportElement[] elements = importStatement.getImportElements();
      for (PyImportElement element : elements) {
        if (!unusedImports.contains(element)) {
          return false;
        }
      }
      return true;
    }

    public void optimizeImports() {
      final List<PsiElement> elementsToDelete = collectUnusedImportElements();
      for (PsiElement element : elementsToDelete) {
        PyPsiUtils.assertValid(element);
        element.delete();
      }
    }

    @Override
    boolean ignoreUnresolved(@NotNull PyElement node, @NotNull PsiReference reference) {
      boolean ignoreUnresolved = false;
      for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
        if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
          ignoreUnresolved = true;
          break;
        }
      }
      return ignoreUnresolved;
    }
  }
}
