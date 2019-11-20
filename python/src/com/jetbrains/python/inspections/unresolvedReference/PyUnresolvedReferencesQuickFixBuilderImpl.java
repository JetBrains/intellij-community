// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.AutoImportHintAction;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
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
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PyUnresolvedReferencesQuickFixBuilderImpl implements PyUnresolvedReferencesQuickFixBuilder {
  private final List<LocalQuickFix> myActions = new ArrayList<>(2);

  @Override
  public void addAddInstallPackageQuickFix(@NotNull PyElement node,
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
          StreamEx
            .of(packageName)
            .append(PyPsiPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, Collections.emptyList()))
            .filter(PyPIPackageUtil.INSTANCE::isInPyPI)
            .forEach(pkg -> addInstallPackageAction(pkg, module, sdk));
        }
      }
    }
  }

  @Override
  public void addAddIgnoredIdentifierQuickFix(List<QualifiedName> qualifiedNames) {
    if (qualifiedNames.size() == 1) {
      final QualifiedName qualifiedName = qualifiedNames.get(0);
      myActions.add(new AddIgnoredIdentifierQuickFix(qualifiedName, false));
      if (qualifiedName.getComponentCount() > 1) {
        myActions.add(new AddIgnoredIdentifierQuickFix(qualifiedName.removeLastComponent(), true));
      }
    }
  }

  @Override
  public void addImportStatementQuickFix(PsiElement element) {
    PyImportStatementBase importStatementBase = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class);
    if ((importStatementBase != null) && GenerateBinaryStubsFix.isApplicable(importStatementBase)) {
      myActions.addAll(GenerateBinaryStubsFix.generateFixes(importStatementBase));
    }
  }

  @Override
  public void addRenameUnresolvedRefQuickFix() {
    myActions.add(new PyRenameUnresolvedRefQuickFix());
  }

  @Override
  public void addAddParameterQuickFix(String refName, PyReferenceExpression expr) {
    final PyFunction parentFunction = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(expr, PyDecorator.class);
    final PyAnnotation annotation = PsiTreeUtil.getParentOfType(expr, PyAnnotation.class);
    final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(expr, PyImportStatement.class);
    if (parentFunction != null && decorator == null && annotation == null && importStatement == null) {
      myActions.add(new UnresolvedReferenceAddParameterQuickFix(refName));
    }
  }

  @Override
  public void addCreateFunctionQuickFix(PyReferenceExpression expr) {
    PyCallExpression callExpression = PsiTreeUtil.getParentOfType(expr, PyCallExpression.class);
    if (callExpression != null && (!(callExpression.getCallee() instanceof PyQualifiedExpression) ||
                                   ((PyQualifiedExpression)callExpression.getCallee()).getQualifier() == null)) {
      myActions.add(new UnresolvedRefCreateFunctionQuickFix(callExpression, expr));
    }
  }

  @Override
  public void addTrueFalseQuickFix(PyReferenceExpression expr, String refText) {
    if (refText.equals("true") || refText.equals("false")) {
      myActions.add(new UnresolvedRefTrueFalseQuickFix(expr));
    }
  }

  @Override
  public void addInstallPackageAction(String packageName, Module module, Sdk sdk) {
    final List<PyRequirement> requirements = Collections.singletonList(PyRequirementsKt.pyRequirement(packageName));
    final String name = "Install package " + packageName;
    myActions.add(new PyPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements));
  }

  @Override
  public void addCreateMemberFromUsageFixes(
    TypeEvalContext typeEvalContext, PyType type, PsiReference reference, String refText
  ) {
    PsiElement element = reference.getElement();
    if (type instanceof PyClassTypeImpl) {
      PyClass cls = ((PyClassType)type).getPyClass();
      if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
        if (element.getParent() instanceof PyCallExpression) {
          myActions.add(new AddMethodQuickFix(refText, cls.getName(), true));
        }
        else if (!(reference instanceof PyOperatorReference)) {
          myActions.add(new AddFieldQuickFix(refText, "None", type.getName(), true));
        }
      }
    }
    else if (type instanceof PyModuleType) {
      PyFile file = ((PyModuleType)type).getModule();
      myActions.add(new AddFunctionQuickFix(refText, file.getName()));
      addCreateClassFix(typeEvalContext, refText, element);
    }
  }

  @Override
  public void addAddSelfFix(TypeEvalContext typeEvalContext, PyElement node, PyReferenceExpression expr) {
    final PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
    final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (containedClass != null && function != null) {
      final PyParameter[] parameters = function.getParameterList().getParameters();
      if (parameters.length == 0) return;
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
          myActions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
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
                  myActions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                }
              }
            }
          }
        }
      }
      for (PyFunction method : containedClass.getMethods()) {
        if (expr.getText().equals(method.getName())) {
          myActions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
        }
      }
    }
  }

  @Override
  public void addAutoImportFix(PyElement node, PsiReference reference, PsiElement element) {
    // look in other imported modules for this whole name
    if (PythonImportUtils.isImportable(element)) {
      addAutoImportFix(node, reference);
    }
  }

  private void addAutoImportFix(PyElement node, PsiReference reference) {
    final PsiFile file = InjectedLanguageManager.getInstance(node.getProject()).getTopLevelFile(node);
    if (!(file instanceof PyFile)) return;
    AutoImportQuickFix importFix = PythonImportUtils.proposeImportFix(node, reference);
    if (importFix != null) {
      if (!suppressHintForAutoImport(node, importFix) && PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
        final AutoImportHintAction autoImportHintAction = new AutoImportHintAction(importFix);
        myActions.add(autoImportHintAction);
      }
      else {
        myActions.add(importFix);
      }
      if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
        myActions.add(importFix.forLocalImport());
      }
    }
    else {
      final String refName = (node instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)node).getReferencedName() : node.getText();
      if (refName == null) return;
      final QualifiedName qname = QualifiedName.fromDottedString(refName);
      final List<String> components = qname.getComponents();
      if (!components.isEmpty()) {
        final String packageName = components.get(0);
        final Module module = ModuleUtilCore.findModuleForPsiElement(node);
        if (PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkUtil.findPythonSdk(module) != null) {
          myActions.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageName, packageName, node));
        }
        else {
          final String packageAlias = PyPackageAliasesProvider.commonImportAliases.get(packageName);
          if (packageAlias != null && PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkUtil.findPythonSdk(module) != null) {
            myActions.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageAlias, packageName, node));
          }
        }
      }
    }
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
  public void addCreateClassFix(TypeEvalContext typeEvalContext, @NonNls String refText, PsiElement element) {
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
          myActions.add(new CreateClassQuickFix(refText, anchor));
        }
      }
    }
  }

  private static boolean isCall(PyElement node) {
    final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(node, PyCallExpression.class);
    return callExpression != null && node == callExpression.getCallee();
  }

  @Override
  public void addPluginQuickFixes(PsiReference reference) {
    for (PyUnresolvedReferenceQuickFixProvider provider : PyUnresolvedReferenceQuickFixProvider.EP_NAME.getExtensionList()) {
      provider.registerQuickFixes(reference, localQuickFix -> myActions.add(localQuickFix));
    }
  }

  @Override
  public void addOptimizeImportsQuickFix() {
    myActions.add(new OptimizeImportsQuickFix());
  }

  @Override
  public LocalQuickFix[] build() {
    return myActions.toArray(LocalQuickFix.EMPTY_ARRAY);
  }
}
