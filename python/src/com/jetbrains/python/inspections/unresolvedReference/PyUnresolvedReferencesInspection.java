// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.*;
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
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.packaging.PyRequirementsKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
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
    final ListEditForm form = new ListEditForm("Ignore references", ignoredIdentifiers);
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

    @Override
    public LocalQuickFix getRenameUnresolvedRefQuickFix() {
      return new PyRenameUnresolvedRefQuickFix();
    }

    @Override
    public LocalQuickFix getAddParameterQuickFix(String refName, PyReferenceExpression expr) {
      final PyFunction parentFunction = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
      final PyDecorator decorator = PsiTreeUtil.getParentOfType(expr, PyDecorator.class);
      final PyAnnotation annotation = PsiTreeUtil.getParentOfType(expr, PyAnnotation.class);
      final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(expr, PyImportStatement.class);
      if (parentFunction != null && decorator == null && annotation == null && importStatement == null) {
        return new UnresolvedReferenceAddParameterQuickFix(refName);
      }
      return null;
    }

    @Override
    public LocalQuickFix getCreateFunctionQuickFix(PyReferenceExpression expr) {
      PyCallExpression callExpression = PsiTreeUtil.getParentOfType(expr, PyCallExpression.class);
      if (callExpression != null && (!(callExpression.getCallee() instanceof PyQualifiedExpression) ||
                                     ((PyQualifiedExpression)callExpression.getCallee()).getQualifier() == null)) {
        return new UnresolvedRefCreateFunctionQuickFix(callExpression, expr);
      }
      return null;
    }

    @Override
    public LocalQuickFix getTrueFalseQuickFix(PyReferenceExpression expr, String refText) {
      if (refText.equals("true") || refText.equals("false")) {
        return new UnresolvedRefTrueFalseQuickFix(expr);
      }
      return null;
    }

    private static LocalQuickFix getInstallPackageAction(String packageName, Module module, Sdk sdk) {
      final List<PyRequirement> requirements = Collections.singletonList(PyRequirementsKt.pyRequirement(packageName));
      final String name = PyBundle.message("python.unresolved.reference.inspection.install.package", packageName);
      return new PyPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements);
    }

    @Override
    public Iterable<LocalQuickFix> getCreateMemberFromUsageFixes(
      TypeEvalContext typeEvalContext, PyType type, PsiReference reference, String refText
    ) {
      List<LocalQuickFix> result = new ArrayList<>();
      PsiElement element = reference.getElement();
      if (type instanceof PyClassTypeImpl) {
        PyClass cls = ((PyClassType)type).getPyClass();
        if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
          if (element.getParent() instanceof PyCallExpression) {
            result.add(new AddMethodQuickFix(refText, cls.getName(), true));
          }
          else if (!(reference instanceof PyOperatorReference)) {
            result.add(new AddFieldQuickFix(refText, "None", type.getName(), true));
          }
        }
      }
      else if (type instanceof PyModuleType) {
        PyFile file = ((PyModuleType)type).getModule();
        result.add(new AddFunctionQuickFix(refText, file.getName()));
        getCreateClassFix(typeEvalContext, refText, element);
      }
      return result;
    }

    @Override
    public Iterable<LocalQuickFix> getAddSelfFixes(TypeEvalContext typeEvalContext, PyElement node, PyReferenceExpression expr) {
      List<LocalQuickFix> result = new ArrayList<>();
      final PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
      if (containedClass != null && function != null) {
        final PyParameter[] parameters = function.getParameterList().getParameters();
        if (parameters.length == 0) return Collections.emptyList();
        final String qualifier = parameters[0].getText();
        final PyDecoratorList decoratorList = function.getDecoratorList();
        boolean isClassMethod = false;
        if (decoratorList != null) {
          for (PyDecorator decorator : decoratorList.getDecorators()) {
            final PyExpression callee = decorator.getCallee();
            if (callee != null && PyNames.CLASSMETHOD.equals(callee.getText())) {
              isClassMethod = true;
            }
          }
        }
        for (PyTargetExpression target : containedClass.getInstanceAttributes()) {
          if (!isClassMethod && Comparing.strEqual(node.getName(), target.getName())) {
            result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
          }
        }
        for (PyStatement statement : containedClass.getStatementList().getStatements()) {
          if (statement instanceof PyAssignmentStatement) {
            PyExpression lhsExpression = ((PyAssignmentStatement)statement).getLeftHandSideExpression();
            if (lhsExpression != null && lhsExpression.getText().equals(expr.getText())) {
              PyExpression assignedValue = ((PyAssignmentStatement)statement).getAssignedValue();
              if (assignedValue instanceof PyCallExpression) {
                PyType type = typeEvalContext.getType(assignedValue);
                if (type instanceof PyClassTypeImpl) {
                  if (((PyCallExpression)assignedValue).isCalleeText(PyNames.PROPERTY)) {
                    result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                  }
                }
              }
            }
          }
        }
        for (PyFunction method : containedClass.getMethods()) {
          if (expr.getText().equals(method.getName())) {
            result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
          }
        }
      }
      return result;
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

    @Override
    public LocalQuickFix getCreateClassFix(TypeEvalContext typeEvalContext, @NonNls String refText, PsiElement element) {
      if (refText.length() > 2 && Character.isUpperCase(refText.charAt(0)) && !StringUtil.toUpperCase(refText).equals(refText) &&
          PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) == null) {
        PsiElement anchor = element;
        if (element instanceof PyQualifiedExpression) {
          final PyExpression expr = ((PyQualifiedExpression)element).getQualifier();
          if (expr != null) {
            final PyType type = typeEvalContext.getType(expr);
            if (type instanceof PyModuleType) {
              anchor = ((PyModuleType)type).getModule();
            }
            else {
              anchor = null;
            }
          }
          if (anchor != null) {
            return new CreateClassQuickFix(refText, anchor);
          }
        }
      }
      return null;
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
