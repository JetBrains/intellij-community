// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.ide.IdeBundle;
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
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author vlan
 */
public final class PyPackageManagerUI {
  @NotNull private static final Logger LOG = Logger.getInstance(PyPackageManagerUI.class);

  @Nullable private final Listener myListener;
  @NotNull private final Project myProject;
  @NotNull private final Sdk mySdk;

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

  public void install(@Nullable final List<PyRequirement> requirements, @NotNull final List<String> extraArgs) {
    ProgressManager.getInstance().run(new InstallTask(myProject, mySdk, requirements, extraArgs, myListener));
  }

  public void uninstall(@NotNull final List<PyPackage> packages) {
    if (checkDependents(packages)) {
      return;
    }
    ProgressManager.getInstance().run(new UninstallTask(myProject, mySdk, myListener, packages));
  }

  private boolean checkDependents(@NotNull List<PyPackage> packages) {
    try {
      Map<String, Set<PyPackage>> dependentPackages = collectDependents(packages, mySdk);
      if (dependentPackages.isEmpty()) {
        return false;
      }

      boolean[] warning = {true};
      ApplicationManager.getApplication().invokeAndWait(() -> {
        if (dependentPackages.size() == 1) {
          Map.Entry<String, Set<PyPackage>> packageToDependents = ContainerUtil.getOnlyItem(dependentPackages.entrySet());
          assert packageToDependents != null;
          Set<PyPackage> dependents = packageToDependents.getValue();
          String message = PyBundle.message("python.packaging.dialog.description.attempt.to.uninstall.for.one.dependent.package",
                                            packageToDependents.getKey(), StringUtil.join(dependents, ", "), dependents.size());
          warning[0] =
            MessageDialogBuilder.yesNo(PyBundle.message("python.packaging.warning"), message)
              .asWarning()
              .ask(myProject);
        }
        else {
          List<String> dep = new ArrayList<>();
          for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
            dep.add(PyBundle.message(
              "python.packaging.dialog.description.attempt.to.uninstall.for.several.dependent.packages.single.package.description",
              entry.getKey(),
              StringUtil.join(entry.getValue(), ", ")));
          }
          String message = PyBundle.message("python.packaging.dialog.description.attempt.to.uninstall.for.several.dependent.packages",
                                            StringUtil.join(dep, "\n"));
          warning[0] = MessageDialogBuilder.yesNo(PyBundle.message("python.packaging.warning"), message)
            .asWarning()
            .ask(myProject);
        }
      }, ModalityState.current());
      if (!warning[0]) {
        return true;
      }
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
      if (!dependents.isEmpty()) {
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

    PackagingTask(@Nullable Project project,
                  @NotNull Sdk sdk,
                  @NotNull @NlsContexts.ProgressTitle String title,
                  @Nullable Listener listener) {
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
    @NlsContexts.NotificationTitle
    protected abstract String getSuccessTitle();

    @NotNull
    @NlsContexts.NotificationContent
    protected abstract String getSuccessDescription();

    @NotNull
    @NlsContexts.NotificationTitle
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
        final List<Pair<String, String>> requirements =
          this instanceof InstallTask && ((InstallTask)this).myRequirements != null ? ContainerUtil.flatMap(
            ((InstallTask)this).myRequirements,
            req -> ContainerUtil.map(req.getInstallOptions(), option -> Pair.create(option, req.getName()))) : null;
        final List<String> packageManagerArguments = exceptions.stream()
          .flatMap(e -> (e instanceof PyExecutionException) ? ((PyExecutionException)e).getArgs().stream() : null)
          .collect(Collectors.toList());
        final String packageNames = requirements != null ? requirements.stream()
          .filter(req -> packageManagerArguments.contains(req.first))
          .map(req -> req.second)
          .collect(Collectors.joining(", ")) : "";

        final PyPackageManagementService.PyPackageInstallationErrorDescription description = PyPackageManagementService.
          toErrorDescription(exceptions, mySdk, packageNames);
        if (description != null) {
          final String firstLine = PyBundle.message("python.packaging.notification.title.error.occurred", getTitle());
          final NotificationListener listener = new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent event) {
              assert myProject != null;
              final PyPackageInstallationErrorDialog dialog =
                new PyPackageInstallationErrorDialog(packageNames.isEmpty()
                                                     ? IdeBundle.message("failed.to.install.packages.dialog.title")
                                                     : IdeBundle.message("failed.to.install.package.dialog.title", packageNames),
                                                     description);
              dialog.show();
            }
          };
          String content = wrapIntoLink(firstLine, "python.packaging.notification.description.details.link");
          notificationRef.set(new PackagingNotification(PACKAGING_GROUP_ID, getFailureTitle(), content,
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

    @SuppressWarnings("HardCodedStringLiteral")
    private static @NotNull @Nls String wrapIntoLink(@NotNull @Nls String prefix,
                                                     @NotNull @PropertyKey(resourceBundle = PyBundle.BUNDLE) String key) {
      return prefix + " <a href=\"xxx\">" + PyBundle.message(key) + "</a>";
    }

    private static class PackagingNotification extends Notification {
      PackagingNotification(@NotNull String groupDisplayId,
                            @NotNull @NlsContexts.NotificationTitle String title,
                            @NotNull @NlsContexts.NotificationContent String content,
                            @NotNull NotificationType type,
                            @Nullable NotificationListener listener) {
        super(groupDisplayId, title, content, type);
        if (listener != null) setListener(listener);
      }
    }
  }

  private static class InstallTask extends PackagingTask {
    @Nullable private final List<PyRequirement> myRequirements;
    @NotNull private final List<String> myExtraArgs;

    InstallTask(@Nullable Project project,
                @NotNull Sdk sdk,
                @Nullable List<PyRequirement> requirements,
                @NotNull List<String> extraArgs,
                @Nullable Listener listener) {
      super(project, sdk, PyBundle.message("python.packaging.progress.title.installing.packages"), listener);
      myRequirements = requirements;
      myExtraArgs = extraArgs;
    }

    @NotNull
    @Override
    protected List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      if (myRequirements == null) {
        indicator.setText(PyBundle.message("python.packaging.installing.packages"));
        indicator.setIndeterminate(true);
        try {
          manager.install(null, myExtraArgs);
        }
        catch (ExecutionException e) {
          exceptions.add(e);
        }
      }
      else {
        final int size = myRequirements.size();
        for (int i = 0; i < size; i++) {
          final PyRequirement requirement = myRequirements.get(i);
          indicator.setText(PyBundle.message("python.packaging.progress.text.installing.specific.package",
                                             requirement.getPresentableText()));
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
      }
      manager.refresh();
      return exceptions;
    }

    @NotNull
    @Override
    protected String getSuccessTitle() {
      return PyBundle.message("python.packaging.notification.title.packages.installed.successfully");
    }

    @NotNull
    @Override
    protected String getSuccessDescription() {
      return myRequirements != null
             ? PyBundle.message("python.packaging.notification.description.installed.packages",
                                PyPackageUtil.requirementsToString(myRequirements))
             : PyBundle.message("python.packaging.notification.description.installed.all.requirements");
    }

    @NotNull
    @Override
    protected String getFailureTitle() {
      return PyBundle.message("python.packaging.notification.title.install.packages.failed");
    }
  }

  private static class InstallManagementTask extends InstallTask {

    InstallManagementTask(@Nullable Project project,
                          @NotNull Sdk sdk,
                          @Nullable Listener listener) {
      super(project, sdk, Collections.emptyList(), Collections.emptyList(), listener);
    }

    @NotNull
    @Override
    protected List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      final PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
      indicator.setText(PyBundle.message("python.packaging.installing.packaging.tools"));
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
      return PyBundle.message("python.packaging.notification.description.installed.python.packaging.tools");
    }
  }

  private static class UninstallTask extends PackagingTask {
    @NotNull private final List<PyPackage> myPackages;

    UninstallTask(@Nullable Project project,
                  @NotNull Sdk sdk,
                  @Nullable Listener listener,
                  @NotNull List<PyPackage> packages) {
      super(project, sdk, PyBundle.message("python.packaging.progress.title.uninstalling.packages"), listener);
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
      return PyBundle.message("python.packaging.notification.title.packages.uninstalled.successfully");
    }

    @NotNull
    @Override
    protected String getSuccessDescription() {
      final String packagesString = StringUtil.join(myPackages, pkg -> "'" + pkg.getName() + "'", ", ");
      return PyBundle.message("python.packaging.notification.description.uninstalled.packages", packagesString);
    }

    @NotNull
    @Override
    protected String getFailureTitle() {
      return PyBundle.message("python.packaging.notification.title.uninstall.packages.failed");
    }
  }
}
