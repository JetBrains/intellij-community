package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Warns about shadowing built-in names.
 *
 * TODO: Merge into PyRedeclarationInspection and detect all shadowed names
 *
 * @author vlan
 */
public class PyShadowingBuiltinsInspection extends PyInspection {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      processElement(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      if (node.getContainingClass() == null) {
        processElement(node);
      }
    }

    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
      processElement(node);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      if (node.getQualifier() == null) {
        processElement(node);
      }
    }

    private void processElement(@NotNull PsiNameIdentifierOwner element) {
      final String name = element.getName();
      if (name != null) {
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
        final PsiElement builtin = builtinCache.getByName(name);
        if (builtin != null && !PyUtil.inSameFile(builtin, element)) {
          final PsiElement identifier = element.getNameIdentifier();
          registerProblem(identifier != null ? identifier : element, "Shadows a built-in with the same name",
                          new PyRenameElementQuickFix());
        }
      }
    }

    private static class PyRenameElementQuickFix implements LocalQuickFix {
      @NotNull
      @Override
      public String getName() {
        return "Rename element";
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return "Rename element";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        final PsiNameIdentifierOwner nameOwner = element instanceof PsiNameIdentifierOwner ?
                                                 (PsiNameIdentifierOwner)element :
                                                 PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner.class, true);
        if (nameOwner != null) {
          final VirtualFile virtualFile = nameOwner.getContainingFile().getVirtualFile();
          if (virtualFile != null) {
            final Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
            if (ApplicationManager.getApplication().isUnitTestMode()) {
              renameInUnitTestMode(project, nameOwner, editor);
            }
            else {
              if (nameOwner instanceof PyNamedParameter || nameOwner instanceof PyTargetExpression) {
                new VariableInplaceRenamer(nameOwner, editor).performInplaceRename();
              }
              else {
                PsiElementRenameHandler.invoke(nameOwner, project, ScopeUtil.getScopeOwner(nameOwner), editor);
              }
            }
          }
        }
      }

      private static void renameInUnitTestMode(@NotNull Project project, @NotNull PsiNameIdentifierOwner nameOwner,
                                               @Nullable Editor editor) {
        final PsiElement substitution = RenamePsiElementProcessor.forElement(nameOwner).substituteElementToRename(nameOwner, editor);
        if (substitution != null) {
          new RenameProcessor(project, substitution, "a", false, false).run();
        }
      }
    }
  }
}
