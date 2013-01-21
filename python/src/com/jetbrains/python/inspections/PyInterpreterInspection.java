package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 * User: ktisha
 */
public class PyInterpreterInspection extends PyInspection {

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.invalid.interpreter");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(PyFile node) {
      super.visitPyFile(node);
      if (PlatformUtils.isPyCharm()) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(node);
        if (module != null) {
          final Sdk sdk = PythonSdkType.findPythonSdk(module);
          if (sdk == null) {
            registerProblem(node, "No Python interpreter configured for the project", new ConfigureInterpreterFix());
          }
          else if (PythonSdkType.isInvalid(sdk)) {
            registerProblem(node, "Invalid Python interpreter selected for the project", new ConfigureInterpreterFix());
          }
        }
      }
    }
  }

  private static class ConfigureInterpreterFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return "Configure Python Interpreter";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Configure Python Interpreter";
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          // outside of read action
          ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter");
        }
      });
    }
  }
}
