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

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
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
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
* @author vlan
*/
public class PyPackageManagerUI {
  private static final Logger LOG = Logger.getInstance(PyPackageManagerUI.class);
  @Nullable private Listener myListener;
  @NotNull private Project myProject;
  @NotNull private Sdk mySdk;

  public interface Listener {
    void started();

    void finished(List<PyExternalProcessException> exceptions);
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
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            if (dependentPackages.size() == 1) {
              String message = "You are attempting to uninstall ";
              List<String> dep = new ArrayList<String>();
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
              List<String> dep = new ArrayList<String>();
              for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
                dep.add(entry.getKey() + " -> " + StringUtil.join(entry.getValue(), ", "));
              }
              message += StringUtil.join(dep, "\n");
              message += "\n\nDo you want to proceed?";
              warning[0] = Messages.showYesNoDialog(message, "Warning",
                                                    AllIcons.General.BalloonWarning);
            }
          }
        }, ModalityState.current());
      }
      if (warning[0] != Messages.YES) return true;
    }
    catch (PyExternalProcessException e) {
      LOG.info("Error loading packages dependents: " + e.getMessage(), e);
    }
    return false;
  }

  private static Map<String, Set<PyPackage>> collectDependents(@NotNull final List<PyPackage> packages, Sdk sdk)
    throws PyExternalProcessException {
    Map<String, Set<PyPackage>> dependentPackages = new HashMap<String, Set<PyPackage>>();
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

    @Nullable protected final Listener myListener;

    public PackagingTask(@Nullable Project project, @NotNull String title, @Nullable Listener listener) {
      super(project, title);
      myListener = listener;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      taskStarted(indicator);
      taskFinished(runTask(indicator));
    }

    @NotNull
    protected abstract List<PyExternalProcessException> runTask(@NotNull ProgressIndicator indicator);

    @NotNull
    protected abstract String getSuccessTitle();

    @NotNull
    protected abstract String getSuccessDescription();

    @NotNull
    protected abstract String getFailureTitle();

    protected void taskStarted(@NotNull ProgressIndicator indicator) {
      indicator.setText(getTitle() + "...");
      if (myListener != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myListener.started();
          }
        });
      }
    }

    protected void taskFinished(@NotNull final List<PyExternalProcessException> exceptions) {
      final Ref<Notification> notificationRef = new Ref<Notification>(null);
      if (exceptions.isEmpty()) {
        notificationRef.set(new Notification(PACKAGING_GROUP_ID, getSuccessTitle(), getSuccessDescription(),
                                             NotificationType.INFORMATION));
      }
      else {
        final String firstLine = getTitle() + ": error occurred.";
        final String description = createDescription(exceptions, firstLine);
        notificationRef.set(new Notification(PACKAGING_GROUP_ID, getFailureTitle(),
                                             firstLine + " <a href=\"xxx\">Details...</a>",
                                             NotificationType.ERROR,
                                             new NotificationListener() {
                                               @Override
                                               public void hyperlinkUpdate(@NotNull Notification notification,
                                                                           @NotNull HyperlinkEvent event) {
                                                 assert myProject != null;
                                                 PackagesNotificationPanel.showError(myProject, getFailureTitle(), description);
                                               }
                                             }
        ));
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myListener != null) {
            myListener.finished(exceptions);
          }
          final Notification notification = notificationRef.get();
          if (notification != null) {
            notification.notify(myProject);
          }
        }
      });
    }
  }

  private static class InstallTask extends PackagingTask {
    @NotNull protected final Sdk mySdk;
    @NotNull private final List<PyRequirement> myRequirements;
    @NotNull private final List<String> myExtraArgs;

    public InstallTask(@Nullable Project project,
                       @NotNull Sdk sdk,
                       @NotNull List<PyRequirement> requirements,
                       @NotNull List<String> extraArgs,
                       @Nullable Listener listener) {
      super(project, "Installing packages", listener);
      mySdk = sdk;
      myRequirements = requirements;
      myExtraArgs = extraArgs;
    }

    @NotNull
    @Override
    protected List<PyExternalProcessException> runTask(@NotNull ProgressIndicator indicator) {
      final List<PyExternalProcessException> exceptions = new ArrayList<PyExternalProcessException>();
      final int size = myRequirements.size();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      for (int i = 0; i < size; i++) {
        final PyRequirement requirement = myRequirements.get(i);
        indicator.setText(String.format("Installing package '%s'...", requirement));
        indicator.setFraction((double)i / size);
        try {
          manager.install(Arrays.asList(requirement), myExtraArgs);
        }
        catch (PyExternalProcessException e) {
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
    protected List<PyExternalProcessException> runTask(@NotNull ProgressIndicator indicator) {
      final List<PyExternalProcessException> exceptions = new ArrayList<PyExternalProcessException>();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      indicator.setText("Installing packaging tools...");
      indicator.setIndeterminate(true);
      try {
        manager.installManagement();
      }
      catch (PyExternalProcessException e) {
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
    @NotNull private final Sdk mySdk;
    @NotNull private final List<PyPackage> myPackages;

    public UninstallTask(@Nullable Project project,
                         @NotNull Sdk sdk,
                         @Nullable Listener listener,
                         @NotNull List<PyPackage> packages) {
      super(project, "Uninstalling packages", listener);
      mySdk = sdk;
      myPackages = packages;
    }

    @NotNull
    @Override
    protected List<PyExternalProcessException> runTask(@NotNull ProgressIndicator indicator) {
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      try {
        manager.uninstall(myPackages);
        return Arrays.asList();
      }
      catch (PyExternalProcessException e) {
        return Arrays.asList(e);
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
      final String packagesString = StringUtil.join(myPackages, new Function<PyPackage, String>() {
        @Override
        public String fun(PyPackage pkg) {
          return "'" + pkg.getName() + "'";
        }
      }, ", ");
      return "Uninstalled packages: " + packagesString;
    }

    @NotNull
    @Override
    protected String getFailureTitle() {
      return "Uninstall packages failed";
    }
  }

  public static String createDescription(List<PyExternalProcessException> exceptions, String firstLine) {
    final StringBuilder b = new StringBuilder();
    b.append(firstLine);
    b.append("\n\n");
    for (PyExternalProcessException exception : exceptions) {
      b.append(exception.toString());
      b.append("\n");
    }
    return b.toString();
  }
}
