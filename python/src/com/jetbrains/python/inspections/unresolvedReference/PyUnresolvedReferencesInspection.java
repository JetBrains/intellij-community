// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PythonRuntimeService;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.AutoImportHintAction;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.PyPackageRequirementsInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider;
import com.jetbrains.python.inspections.quickfix.AddIgnoredIdentifierQuickFix;
import com.jetbrains.python.inspections.quickfix.GenerateBinaryStubsFix;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.packaging.PyRequirementsKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 * User: dcheryasov
 */
public class PyUnresolvedReferencesInspection extends PyInspection {
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
    session.putUserData(PyUnresolvedReferencesVisitor.INSPECTION, this);
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
    final ListEditForm form = new ListEditForm(PyPsiBundle.message("INSP.unresolved.refs.column.name.ignore.references"),
                                               ignoredIdentifiers);
    return form.getContentPanel();
  }

  public static class Visitor extends PyUnresolvedReferencesVisitor {
    private volatile Boolean myIsEnabled = null;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, List<String> ignoredIdentifiers) {
      super(holder, session, ignoredIdentifiers);
    }

    @Override
    public boolean isEnabled(@NotNull PsiElement anchor) {
      if (myIsEnabled == null) {
        final boolean isPyCharm = PlatformUtils.isPyCharm();
        Boolean overridden = overriddenUnresolvedReferenceInspection(anchor.getContainingFile());
        if (overridden != null) {
          myIsEnabled = overridden;
        }
        else if (PySkeletonRefresher.isGeneratingSkeletons()) {
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

    private static @Nullable Boolean overriddenUnresolvedReferenceInspection(@NotNull PsiFile file) {
      return PyInspectionExtension.EP_NAME.getExtensionList().stream()
        .map(e -> e.overrideUnresolvedReferenceInspection(file))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
    }

    public void highlightUnusedImports() {
      final List<PyInspectionExtension> extensions = PyInspectionExtension.EP_NAME.getExtensionList();
      final List<PsiElement> unused = collectUnusedImportElements();
      for (PsiElement element : unused) {
        if (extensions.stream().anyMatch(extension -> extension.ignoreUnused(element, myTypeEvalContext))) {
          continue;
        }
        if (element.getTextLength() > 0) {
          OptimizeImportsQuickFix fix = new OptimizeImportsQuickFix();
          registerProblem(element, PyPsiBundle.message("INSP.unused.import.statement"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, fix);
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
                        PyPsiBundle.message("INSP.try.except.import.error",
                                            importElement.getVisibleName()),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }

    @Override
    public Iterable<LocalQuickFix> getInstallPackageQuickFixes(@NotNull PyElement node,
                                                               @NotNull PsiReference reference,
                                                               String refName) {
      if (reference instanceof PyImportReference) {
        // TODO: Ignore references in the second part of the 'from ... import ...' expression
        final QualifiedName qname = QualifiedName.fromDottedString(refName);
        final List<String> components = qname.getComponents();
        if (!components.isEmpty()) {
          final String packageName = components.get(0);
          final Module module = ModuleUtilCore.findModuleForPsiElement(node);
          final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
          if (module != null && sdk != null && PyPackageUtil.packageManagementEnabled(sdk)) {
            return StreamEx
              .of(packageName)
              .append(PyPsiPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, Collections.emptyList()))
              .filter(PyPIPackageUtil.INSTANCE::isInPyPI)
              .map(pkg -> getInstallPackageAction(pkg, module, sdk));
          }
        }
      }
      return Collections.emptyList();
    }

    @Override
    public Iterable<LocalQuickFix> getAddIgnoredIdentifierQuickFixes(List<QualifiedName> qualifiedNames) {
      List<LocalQuickFix> result = new ArrayList<>(2);
      if (qualifiedNames.size() == 1) {
        final QualifiedName qualifiedName = qualifiedNames.get(0);
        result.add(new AddIgnoredIdentifierQuickFix(qualifiedName, false));
        if (qualifiedName.getComponentCount() > 1) {
          result.add(new AddIgnoredIdentifierQuickFix(qualifiedName.removeLastComponent(), true));
        }
      }
      return result;
    }

    @Override
    public Iterable<LocalQuickFix> getImportStatementQuickFixes(PsiElement element) {
      PyImportStatementBase importStatementBase = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class);
      if ((importStatementBase != null) && GenerateBinaryStubsFix.isApplicable(importStatementBase)) {
        return GenerateBinaryStubsFix.generateFixes(importStatementBase);
      }
      return Collections.emptyList();
    }

    private static LocalQuickFix getInstallPackageAction(String packageName, Module module, Sdk sdk) {
      final List<PyRequirement> requirements = Collections.singletonList(PyRequirementsKt.pyRequirement(packageName));
      final String name = PyBundle.message("python.unresolved.reference.inspection.install.package", packageName);
      return new PyPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements);
    }

    @Override
    public Iterable<LocalQuickFix> getAutoImportFixes(PyElement node, PsiReference reference, PsiElement element) {
      // look in other imported modules for this whole name
      if (!PythonImportUtils.isImportable(element)) {
        return Collections.emptyList();
      }
      final PsiFile file = InjectedLanguageManager.getInstance(node.getProject()).getTopLevelFile(node);
      if (!(file instanceof PyFile)) {
        return Collections.emptyList();
      }
      List<LocalQuickFix> result = new ArrayList<>();
      AutoImportQuickFix importFix = PythonImportUtils.proposeImportFix(node, reference);
      if (importFix != null) {
        if (!suppressHintForAutoImport(node, importFix) && PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
          final AutoImportHintAction autoImportHintAction = new AutoImportHintAction(importFix);
          result.add(autoImportHintAction);
        }
        else {
          result.add(importFix);
        }
        if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
          result.add(importFix.forLocalImport());
        }
      }
      else {
        final String refName = (node instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)node).getReferencedName() : node.getText();
        if (refName == null) return result;
        final QualifiedName qname = QualifiedName.fromDottedString(refName);
        final List<String> components = qname.getComponents();
        if (!components.isEmpty()) {
          final String packageName = components.get(0);
          final Module module = ModuleUtilCore.findModuleForPsiElement(node);
          if (PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkUtil.findPythonSdk(module) != null) {
            result.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageName, packageName, node));
          }
          else {
            final String packageAlias = PyPackageAliasesProvider.commonImportAliases.get(packageName);
            if (packageAlias != null && PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkUtil.findPythonSdk(module) != null) {
              result.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageAlias, packageName, node));
            }
          }
        }
      }
      return result;
    }

    private static boolean suppressHintForAutoImport(PyElement node, AutoImportQuickFix importFix) {
      // if the context doesn't look like a function call and we only found imports of functions, suggest auto-import
      // as a quickfix but no popup balloon (PY-2312)
      if (!isCall(node) && importFix.hasOnlyFunctions()) {
        return true;
      }
      // if we're in a class context and the class defines a variable with the same name, offer auto-import only as quickfix,
      // not as popup
      PyClass containingClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (containingClass != null && (containingClass.findMethodByName(importFix.getNameToImport(), true, null) != null ||
                                      containingClass.findInstanceAttribute(importFix.getNameToImport(), true) != null)) {
        return true;
      }
      return false;
    }

    private static boolean isCall(PyElement node) {
      final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(node, PyCallExpression.class);
      return callExpression != null && node == callExpression.getCallee();
    }

    @Override
    public Iterable<LocalQuickFix> getPluginQuickFixes(PsiReference reference) {
      List<LocalQuickFix> result = new ArrayList<>();
      for (PyUnresolvedReferenceQuickFixProvider provider : PyUnresolvedReferenceQuickFixProvider.EP_NAME.getExtensionList()) {
        provider.registerQuickFixes(reference, result::add);
      }
      return result;
    }
  }
}
