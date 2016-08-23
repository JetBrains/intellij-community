/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.webcore.packaging.PackageManagementService;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
* @author vlan
*/
public class PyPackageManagerUI {
  @NotNull private static final Logger LOG = Logger.getInstance(PyPackageManagerUI.class);

  @Nullable private Listener myListener;
  @NotNull private Project myProject;
  @NotNull private Sdk mySdk;

  public interface Listener {
    void started();

    void finished(List<ExecutionException> exceptions);
  }

  public PyPackageManagerUI(@NotNull Project project, @NotNull Sdk sdk, @Nullable Listener listener) {
    myProject = project;
    mySdk = sdk;
    myListener = listener;
  }

  public void installManagement() {
    ProgressManager.getInstance().run(new InstallManagementTask(myProject, mySdk, myListener));
  }

  public void install(@NotNull final List<PyRequirement> requirements, @NotNull final List<String> extraArgs) {
    ProgressManager.getInstance().run(new InstallTask(myProject, mySdk, requirements, extraArgs, myListener));
  }

  public void uninstall(@NotNull final List<PyPackage> packages) {
    if (checkDependents(packages)) {
      return;
    }
    ProgressManager.getInstance().run(new UninstallTask(myProject, mySdk, myListener, packages));
  }

  private boolean checkDependents(@NotNull final List<PyPackage> packages) {
    try {
      final Map<String, Set<PyPackage>> dependentPackages = collectDependents(packages, mySdk);
      final int[] warning = {0};
      if (!dependentPackages.isEmpty()) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          if (dependentPackages.size() == 1) {
            String message = "You are attempting to uninstall ";
            List<String> dep = new ArrayList<>();
            int size = 1;
            for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
              final Set<PyPackage> value = entry.getValue();
              size = value.size();
              dep.add(entry.getKey() + " package which is required for " + StringUtil.join(value, ", "));
            }
            message += StringUtil.join(dep, "\n");
            message += size == 1 ? " package" : " packages";
            message += "\n\nDo you want to proceed?";
            warning[0] = Messages.showYesNoDialog(message, "Warning",
                                                  AllIcons.General.BalloonWarning);
          }
          else {
            String message = "You are attempting to uninstall packages which are required for another packages.\n\n";
            List<String> dep = new ArrayList<>();
            for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
              dep.add(entry.getKey() + " -> " + StringUtil.join(entry.getValue(), ", "));
            }
            message += StringUtil.join(dep, "\n");
            message += "\n\nDo you want to proceed?";
            warning[0] = Messages.showYesNoDialog(message, "Warning",
                                                  AllIcons.General.BalloonWarning);
          }
        }, ModalityState.current());
      }
      if (warning[0] != Messages.YES) return true;
    }
    catch (ExecutionException e) {
      LOG.info("Error loading packages dependents: " + e.getMessage(), e);
    }
    return false;
  }

  private static Map<String, Set<PyPackage>> collectDependents(@NotNull final List<PyPackage> packages,
                                                               Sdk sdk) throws ExecutionException {
    Map<String, Set<PyPackage>> dependentPackages = new HashMap<>();
    for (PyPackage pkg : packages) {
      final Set<PyPackage> dependents = PyPackageManager.getInstance(sdk).getDependents(pkg);
      if (dependents != null && !dependents.isEmpty()) {
        for (PyPackage dependent : dependents) {
          if (!packages.contains(dependent)) {
            dependentPackages.put(pkg.getName(), dependents);
          }
        }
      }
    }
    return dependentPackages;
  }

  private abstract static class PackagingTask extends Task.Backgroundable {
    private static final String PACKAGING_GROUP_ID = "Packaging";

    @NotNull protected final Sdk mySdk;
    @Nullable protected final Listener myListener;

    public PackagingTask(@Nullable Project project, @NotNull Sdk sdk, @NotNull String title, @Nullable Listener listener) {
      super(project, title);
      mySdk = sdk;
      myListener = listener;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      taskStarted(indicator);
      taskFinished(runTask(indicator));
    }

    @NotNull
    protected abstract List<ExecutionException> runTask(@NotNull ProgressIndicator indicator);

    @NotNull
    protected abstract String getSuccessTitle();

    @NotNull
    protected abstract String getSuccessDescription();

    @NotNull
    protected abstract String getFailureTitle();

    protected void taskStarted(@NotNull ProgressIndicator indicator) {
      final PackagingNotification[] notifications =
        NotificationsManager.getNotificationsManager().getNotificationsOfType(PackagingNotification.class, getProject());
      for (PackagingNotification notification : notifications) {
        notification.expire();
      }
      indicator.setText(getTitle() + "...");
      if (myListener != null) {
        ApplicationManager.getApplication().invokeLater(() -> myListener.started());
      }
    }

    protected void taskFinished(@NotNull final List<ExecutionException> exceptions) {
      final Ref<Notification> notificationRef = new Ref<>(null);
      if (exceptions.isEmpty()) {
        notificationRef.set(new PackagingNotification(PACKAGING_GROUP_ID, getSuccessTitle(), getSuccessDescription(),
                                                             NotificationType.INFORMATION, null));
      }
      else {
        final PackageManagementService.ErrorDescription description = PyPackageManagementService.toErrorDescription(exceptions, mySdk);
        if (description != null) {
          final String firstLine = getTitle() + ": error occurred.";
          final NotificationListener listener = new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent event) {
              assert myProject != null;
              final String title = StringUtil.capitalizeWords(getFailureTitle(), true);
              PackagesNotificationPanel.showError(title, description);
            }
          };
          notificationRef.set(new PackagingNotification(PACKAGING_GROUP_ID, getFailureTitle(), firstLine + " <a href=\"xxx\">Details...</a>",
                                               NotificationType.ERROR, listener));
        }
      }
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myListener != null) {
          myListener.finished(exceptions);
        }
        final Notification notification = notificationRef.get();
        if (notification != null) {
          notification.notify(myProject);
        }
      });
    }

    private static class PackagingNotification extends Notification{

      public PackagingNotification(@NotNull String groupDisplayId,
                                   @NotNull String title,
                                   @NotNull String content,
                                   @NotNull NotificationType type, @Nullable NotificationListener listener) {
        super(groupDisplayId, title, content, type, listener);
      }
    }
  }

  private static class InstallTask extends PackagingTask {
    @NotNull private final List<PyRequirement> myRequirements;
    @NotNull private final List<String> myExtraArgs;

    public InstallTask(@Nullable Project project,
                       @NotNull Sdk sdk,
                       @NotNull List<PyRequirement> requirements,
                       @NotNull List<String> extraArgs,
                       @Nullable Listener listener) {
      super(project, sdk, "Installing packages", listener);
      myRequirements = requirements;
      myExtraArgs = extraArgs;
    }

    @NotNull
    @Override
    protected List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      final int size = myRequirements.size();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      for (int i = 0; i < size; i++) {
        final PyRequirement requirement = myRequirements.get(i);
        indicator.setText(String.format("Installing package '%s'...", requirement));
        if (i == 0) {
          indicator.setIndeterminate(true);
        }
        else {
          indicator.setIndeterminate(false);
          indicator.setFraction((double)i / size);
        }
        try {
          manager.install(Collections.singletonList(requirement), myExtraArgs);
        }
        catch (RunCanceledByUserException e) {
          exceptions.add(e);
          break;
        }
        catch (ExecutionException e) {
          exceptions.add(e);
        }
      }
      manager.refresh();
      return exceptions;
    }

    @NotNull
    @Override
    protected String getSuccessTitle() {
      return "Packages installed successfully";
    }

    @NotNull
    @Override
    protected String getSuccessDescription() {
      return "Installed packages: " + PyPackageUtil.requirementsToString(myRequirements);
    }

    @NotNull
    @Override
    protected String getFailureTitle() {
      return "Install packages failed";
    }
  }

  private static class InstallManagementTask extends InstallTask {

    public InstallManagementTask(@Nullable Project project,
                                 @NotNull Sdk sdk,
                                 @Nullable Listener listener) {
      super(project, sdk, Collections.<PyRequirement>emptyList(), Collections.<String>emptyList(), listener);
    }

    @NotNull
    @Override
    protected List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      indicator.setText("Installing packaging tools...");
      indicator.setIndeterminate(true);
      try {
        manager.installManagement();
      }
      catch (ExecutionException e) {
        exceptions.add(e);
      }
      manager.refresh();
      return exceptions;
    }

    @NotNull
    @Override
    protected String getSuccessDescription() {
      return "Installed Python packaging tools";
    }
  }

  private static class UninstallTask extends PackagingTask {
    @NotNull private final List<PyPackage> myPackages;

    public UninstallTask(@Nullable Project project,
                         @NotNull Sdk sdk,
                         @Nullable Listener listener,
                         @NotNull List<PyPackage> packages) {
      super(project, sdk, "Uninstalling packages", listener);
      myPackages = packages;
    }

    @NotNull
    @Override
    protected List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      indicator.setIndeterminate(true);
      try {
        manager.uninstall(myPackages);
        return Collections.emptyList();
      }
      catch (ExecutionException e) {
        return Collections.singletonList(e);
      }
      finally {
        manager.refresh();
      }
    }

    @NotNull
    @Override
    protected String getSuccessTitle() {
      return "Packages uninstalled successfully";
    }

    @NotNull
    @Override
    protected String getSuccessDescription() {
      final String packagesString = StringUtil.join(myPackages, pkg -> "'" + pkg.getName() + "'", ", ");
      return "Uninstalled packages: " + packagesString;
    }

    @NotNull
    @Override
    protected String getFailureTitle() {
      return "Uninstall packages failed";
    }
  }
}
