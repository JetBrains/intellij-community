// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.inspections.quickfix.PyAddPropertyForFieldQuickFix;
import com.jetbrains.python.inspections.quickfix.PyMakePublicQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.testing.PythonUnitTestDetectorsKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: ktisha
 *
 * Inspection to detect situations, where
 * protected member (i.e. class member with a name beginning with an underscore)
 * is access outside the class or a descendant of the class where it's defined.
 */
public class PyProtectedMemberInspection extends PyInspection {
  public boolean ignoreTestFunctions = true;
  public boolean ignoreAnnotations = false;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }


  private class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      final PyStatement statement = node.getContainingImportStatement();
      if (!(statement instanceof PyFromImportStatement)) return;
      final PyReferenceExpression importReferenceExpression = node.getImportReferenceExpression();
      final PyReferenceExpression importSource = ((PyFromImportStatement)statement).getImportSource();
      if (importReferenceExpression != null && importSource != null && !isImportFromTheSamePackage(importSource)) {
        checkReference(importReferenceExpression, importSource);
      }
    }

    private boolean isImportFromTheSamePackage(@NotNull PyReferenceExpression importSource) {
      final PsiDirectory currentFileDirectory = importSource.getContainingFile().getContainingDirectory();

      if (currentFileDirectory != null && PyUtil.isPackage(currentFileDirectory, true, importSource)) {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext);

        return StreamEx
          .of(importSource.getReference(resolveContext).multiResolve(false))
          .map(ResolveResult::getElement)
          .select(PyFile.class)
          .map(PsiFile::getContainingDirectory)
          .nonNull()
          .map(PsiDirectory::getVirtualFile)
          .anyMatch(importedSourceDir -> VfsUtilCore.isAncestor(importedSourceDir, currentFileDirectory.getVirtualFile(), false));
      }

      return false;
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (ignoreAnnotations && PsiTreeUtil.getParentOfType(node, PyAnnotation.class) != null) return;
      if (qualifier == null || ArrayUtil.contains(qualifier.getText(), PyNames.CANONICAL_SELF, PyNames.CANONICAL_CLS)) return;
      if (isImportFromTheSamePackage(node)) return;
      checkReference(node, qualifier);
    }

    private void checkReference(@NotNull final PyReferenceExpression node, @NotNull final PyExpression qualifier) {
      final String name = node.getName();
      final List<LocalQuickFix> quickFixes = new ArrayList<>();
      LocalQuickFix renameElementQuickFix = PythonUiService.getInstance().createPyRenameElementQuickFix(node);
      if (renameElementQuickFix != null) {
        quickFixes.add(renameElementQuickFix);
      }

      if (name != null && name.startsWith("_") && !name.startsWith("__") && !name.endsWith("__")) {
        final PsiReference reference = node.getReference(getResolveContext());
        for (final PyInspectionExtension inspectionExtension : PyInspectionExtension.EP_NAME.getExtensions()) {
          if (inspectionExtension.ignoreProtectedSymbol(node, myTypeEvalContext)) {
            return;
          }
        }
        final PsiElement resolvedExpression = reference.resolve();
        final PyClass resolvedClass = getNotPyiClassOwner(resolvedExpression);

        if (resolvedExpression instanceof PyTargetExpression) {

          final String newName = StringUtil.trimLeading(name, '_');
          if (resolvedClass != null) {

            final String qFixName = resolvedClass.getProperties().containsKey(newName) ?
                                    PyPsiBundle.message("QFIX.use.property") : PyPsiBundle.message("QFIX.add.property");
            quickFixes.add(new PyAddPropertyForFieldQuickFix(qFixName));

            final PyClassType classType = PyUtil.as(myTypeEvalContext.getType(resolvedClass), PyClassType.class);
            if (classType != null) {
              final Set<String> usedNames = classType.getMemberNames(true, myTypeEvalContext);
              if (!usedNames.contains(newName)) {
                quickFixes.add(new PyMakePublicQuickFix());
              }
            }
          }
        }


        if (ignoreTestFunctions) {
          for (PsiElement item = node.getParent(); item != null && !(item instanceof PsiFileSystemItem); item = item.getParent()) {
            if (PythonUnitTestDetectorsKt.isTestElement(item, myTypeEvalContext)) {
              return;
            }
          }
        }

        final PyClass parentClass = getClassOwner(node);
        if (parentClass != null) {

          if (parentClass.isSubclass(resolvedClass, myTypeEvalContext))
            return;

          PyClass outerClass = getClassOwner(parentClass);
          while (outerClass != null) {
            if (outerClass.isSubclass(resolvedClass, myTypeEvalContext))
              return;

            outerClass = getClassOwner(outerClass);
          }
        }
        final PyType type = myTypeEvalContext.getType(qualifier);
        final String bundleKey = type instanceof PyModuleType ? "INSP.protected.member.$0.access.module" : "INSP.protected.member.$0.access";
        registerProblem(node, PyPsiBundle.message(bundleKey, name), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, quickFixes.toArray(
          LocalQuickFix.EMPTY_ARRAY));
      }
    }

    @Nullable
    private PyClass getNotPyiClassOwner(@Nullable PsiElement element) {
      final PyClass owner = getClassOwner(element);
      return owner == null ? null : PyiUtil.getOriginalElementOrLeaveAsIs(owner, PyClass.class);
    }

    @Nullable
    private PyClass getClassOwner(@Nullable PsiElement element) {
      for (ScopeOwner owner = ScopeUtil.getScopeOwner(element); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
        if (owner instanceof PyClass) {
          return (PyClass)owner;
        }
      }
      return null;
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      final PyReferenceExpression source = node.getImportSource();
      if (source == null) return;

      final Set<String> dunderAlls = collectDunderAlls(source);
      if (dunderAlls == null) return;

      StreamEx
        .of(node.getImportElements())
        .map(PyImportElement::getImportReferenceExpression)
        .nonNull()
        .filter(
          referenceExpression -> !dunderAlls.contains(referenceExpression.getName()) && !resolvesToFileSystemItem(referenceExpression)
        )
        .forEach(
          referenceExpression -> {
            final String message = "'" + referenceExpression.getName() + "' is not declared in __all__";
            registerProblem(referenceExpression, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        );
    }

    @Nullable
    private Set<String> collectDunderAlls(@NotNull PyReferenceExpression source) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext);

      final List<List<String>> resolvedDunderAlls = StreamEx
        .of(source.getReference(resolveContext).multiResolve(false))
        .map(ResolveResult::getElement)
        .select(PyFile.class)
        .map(PyFile::getDunderAll)
        .toList();
      if (resolvedDunderAlls.isEmpty()) return null;

      final Set<String> result = new HashSet<>();
      for (List<String> dunderAll : resolvedDunderAlls) {
        if (dunderAll == null) return null;
        result.addAll(dunderAll);
      }
      return result;
    }

    private boolean resolvesToFileSystemItem(@NotNull PyReferenceExpression referenceExpression) {
      final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myTypeEvalContext);

      return ContainerUtil.exists(referenceExpression.getReference(resolveContext).multiResolve(false),
                                  result -> result.getElement() instanceof PsiFileSystemItem);
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(PyPsiBundle.message("INSP.protected.member.ignore.test.functions"), "ignoreTestFunctions");
    panel.addCheckbox(PyPsiBundle.message("INSP.protected.member.ignore.annotations"), "ignoreAnnotations");
    return panel;
  }
}
