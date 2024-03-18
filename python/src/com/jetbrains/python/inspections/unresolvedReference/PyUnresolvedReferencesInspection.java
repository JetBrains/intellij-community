// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.AutoImportHintAction;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyInspectionVisitor;
import com.jetbrains.python.inspections.PyPackageRequirementsInspection;
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider;
import com.jetbrains.python.inspections.quickfix.AddIgnoredIdentifierQuickFix;
import com.jetbrains.python.inspections.quickfix.GenerateBinaryStubsFix;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 */
public final class PyUnresolvedReferencesInspection extends PyUnresolvedReferencesInspectionBase {
  public static final Key<PyUnresolvedReferencesInspection> SHORT_NAME_KEY =
    Key.create(PyUnresolvedReferencesInspection.class.getSimpleName());

  public List<String> ignoredIdentifiers = new ArrayList<>();

  public static PyUnresolvedReferencesInspection getInstance(PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    return (PyUnresolvedReferencesInspection)inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element);
  }

  @Override
  @NotNull
  protected PyUnresolvedReferencesVisitor createVisitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, ignoredIdentifiers, this, PyInspectionVisitor.getContext(session));
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("ignoredIdentifiers", PyPsiBundle.message("INSP.unresolved.refs.ignore.references.label")));
  }

  public static class Visitor extends PyUnresolvedReferencesVisitor {
    public Visitor(@Nullable ProblemsHolder holder,
                   List<String> ignoredIdentifiers,
                   @NotNull PyInspection inspection,
                   @NotNull TypeEvalContext context) {
      super(holder, ignoredIdentifiers, inspection, context);
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
          if (module != null && sdk != null && PyPackageUtil.packageManagementEnabled(sdk, false, true)) {
            return StreamEx
              .of(packageName, PyPsiPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, ""))
              .filter(PyPIPackageUtil.INSTANCE::isInPyPI)
              .map(pkg -> new PyPackageRequirementsInspection.InstallPackageQuickFix(pkg));
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

    @Override
    protected Iterable<LocalQuickFix> getAutoImportFixes(PyElement node, PsiReference reference, PsiElement element) {
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
        String referencedName = node instanceof PyReferenceExpression refExpr && !refExpr.isQualified() ? refExpr.getReferencedName() : null;
        if (referencedName != null && PythonSdkUtil.findPythonSdk(node) != null) {
          if (PyPIPackageUtil.INSTANCE.isInPyPI(referencedName)) {
            result.add(new PyPackageRequirementsInspection.InstallAndImportPackageQuickFix(referencedName, null));
          }
          else {
            String realPackageName = PyPackageAliasesProvider.commonImportAliases.get(referencedName);
            if (realPackageName != null && PyPIPackageUtil.INSTANCE.isInPyPI(realPackageName)) {
              result.add(new PyPackageRequirementsInspection.InstallAndImportPackageQuickFix(realPackageName, referencedName));
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
    void getPluginQuickFixes(List<LocalQuickFix> fixes, PsiReference reference) {
      for (PyUnresolvedReferenceQuickFixProvider provider : PyUnresolvedReferenceQuickFixProvider.EP_NAME.getExtensionList()) {
        provider.registerQuickFixes(reference, fixes);
      }
    }
  }
}
