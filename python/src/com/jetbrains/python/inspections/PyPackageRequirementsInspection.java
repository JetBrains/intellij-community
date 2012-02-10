package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.Function;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspection extends PyInspection {
  private static final Key<Boolean> RUNNING_PACKAGING_TASKS = Key.create("PyPackageRequirementsInspection.RunningPackagingTasks");

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
        if (isRunningPackagingTasks(module)) {
          return;
        }
        final List<PyRequirement> unsatisfied = findUnsatisfiedRequirements(module);
        if (unsatisfied != null && !unsatisfied.isEmpty()) {
          final boolean plural = unsatisfied.size() > 1;
          String msg = String.format("Package requirement%s %s %s not satisfied",
                                     plural ? "s" : "",
                                     requirementsToString(unsatisfied),
                                     plural ? "are" : "is");
          registerProblem(node, msg, new InstallRequirementsFix(module, unsatisfied));
        }
      }
    }
  }

  private static boolean isRunningPackagingTasks(@NotNull Module module) {
    final Boolean value = module.getUserData(RUNNING_PACKAGING_TASKS);
    return value != null && value;
  }

  private static void setRunningPackagingTasks(@NotNull Module module, boolean value) {
    module.putUserData(RUNNING_PACKAGING_TASKS, value);
  }

  @NotNull
  private static String requirementsToString(@NotNull List<PyRequirement> requirements) {
    return StringUtil.join(requirements, new Function<PyRequirement, String>() {
      @Override
      public String fun(PyRequirement requirement) {
        return String.format("'%s'", requirement.toString());
      }
    }, ", ");
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
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final String PROGRESS_TITLE = "Installing requirements";
      ProgressManager.getInstance().run(new Task.Backgroundable(project, PROGRESS_TITLE, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          // TODO: Unify this installation procedure with PythonSdkConfigurable.addUpgradeAction
          final Sdk sdk = PythonSdkType.findPythonSdk(myModule);
          if (sdk != null) {
            final PyPackageManager manager = PyPackageManager.getInstance(sdk);
            indicator.setText(PROGRESS_TITLE + "...");
            final Ref<Notification> notificationRef = new Ref<Notification>(null);
            final String PACKAGING_GROUP_ID = "Packaging";
            final Application application = ApplicationManager.getApplication();
            setRunningPackagingTasks(myModule, true);
            try {
              manager.install(myUnsatisfied);
              final String msg = "Packages installed successfully";
              notificationRef.set(new Notification(PACKAGING_GROUP_ID, msg,
                                                   String.format("Installed packages: " + requirementsToString(myUnsatisfied)),
                                                   NotificationType.INFORMATION));
            }
            catch (final PyExternalProcessException e) {
              final String msg = "Install packages failed";
              final String description = String.format("<html>\n" +
                                                       "  <p>Error occured when installing packages. The following command was executed:</p>\n" +
                                                       "  <br/>\n" +
                                                       "  <p><code>%s %s</code></p>\n" +
                                                       "  <br/>\n" +
                                                       "  <p>The error output of the command:</p>\n" +
                                                       "  <br/>\n" +
                                                       "  <pre><code>%s</code></pre>\n" +
                                                       "</html>",
                                                       e.getName(), StringUtil.join(e.getArgs(), " "), e.getMessage());
              notificationRef.set(new Notification(PACKAGING_GROUP_ID, msg,
                                                   "Error occured when installing packages. <a href=\"xxx\">Details...</a>",
                                                   NotificationType.ERROR,
                                                   new NotificationListener() {
                                                     @Override
                                                     public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                 @NotNull HyperlinkEvent event) {
                                                       Messages.showErrorDialog(myProject, description, StringUtil.capitalize(msg));
                                                       notification.expire();
                                                     }
                                                   }));
            }
            finally {
              setRunningPackagingTasks(myModule, false);
              application.invokeLater(new Runnable() {
                @Override
                public void run() {
                  PythonSdkType.getInstance().setupSdkPaths(sdk);
                  final Notification notification = notificationRef.get();
                  if (notification != null) {
                    notification.notify(project);
                  }
                }
              });
            }
          }
        }
      });
    }
  }
}
