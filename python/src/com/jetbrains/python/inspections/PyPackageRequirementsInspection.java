package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.Function;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspection extends PyInspection {
  @NotNull
  @Override
  public String getDisplayName() {
    return "Package requirements";
  }

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
    public void visitPyFile(PyFile node) {
      final Module module = ModuleUtil.findModuleForPsiElement(node);
      if (module != null) {
        final List<PyRequirement> unsatisfied = findUnsatisfiedRequirements(module);
        if (unsatisfied != null) {
          final int n = unsatisfied.size();
          String msg = null;
          if (n == 1) {
            msg = String.format("Package requirement '%s' is not satisfied", unsatisfied.get(0));
          }
          else if (n > 1) {
            final String pkgs = StringUtil.join(unsatisfied, new Function<PyRequirement, String>() {
              @Override
              public String fun(PyRequirement requirement) {
                return String.format("'%s'", requirement.toString());
              }
            }, ", ");
            msg = String.format("Package requirements %s are not satisfied", pkgs);
          }
          if (msg != null) {
            registerProblem(node, msg, new InstallRequirementsFix(module, unsatisfied));
          }
        }
      }
    }
  }

  @Nullable
  private static List<PyRequirement> findUnsatisfiedRequirements(@NotNull Module module) {
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      final PyPackageManager manager = PyPackageManager.getInstance(sdk);
      List<PyRequirement> requirements = PyPackageManager.getRequirements(module);
      if (requirements != null) {
        final List<PyPackage> packages;
        try {
          packages = manager.getPackages();
        }
        catch (PyExternalProcessException ignored) {
          return null;
        }
        final List<PyRequirement> unsatisfied = new ArrayList<PyRequirement>();
        for (PyRequirement req : requirements) {
          if (!req.match(packages)) {
            unsatisfied.add(req);
          }
        }
        return unsatisfied;
      }
    }
    return null;
  }

  private static class InstallRequirementsFix implements LocalQuickFix {
    private static final String NAME = "Install requirements";
    @NotNull private final Module myModule;
    @NotNull private final List<PyRequirement> myUnsatisfied;

    public InstallRequirementsFix(@NotNull Module module, @NotNull List<PyRequirement> unsatisfied) {
      myModule = module;
      myUnsatisfied = unsatisfied;
    }

    @NotNull
    @Override
    public String getName() {
      return NAME;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return NAME;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Installing requirements", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          // TODO: Unify this installation procedure with PythonSdkConfigurable.addUpgradeAction
          final Sdk sdk = PythonSdkType.findPythonSdk(myModule);
          if (sdk != null) {
            final PyPackageManager manager = PyPackageManager.getInstance(sdk);
            try {
              indicator.setText("Installing requirements...");
              manager.install(myUnsatisfied);
              // TODO: Refresh the filesystem in case of errors too
              VirtualFileManager.getInstance().refresh(true, new Runnable() {
                @Override
                public void run() {
                  PythonSdkUpdater.getInstance().updateActiveSdks(myProject, 0);
                }
              });
              // TODO: Show completion notification
            }
            catch (PyExternalProcessException e) {
              // TODO: Show error notification
              throw new RuntimeException(e);
            }
          }
        }
      });
    }
  }
}
