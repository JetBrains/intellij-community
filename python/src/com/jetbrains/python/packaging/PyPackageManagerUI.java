// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.IdeBundle;
import com.intellij.model.SideEffectGuard;
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.errorProcessing.ExecError;
import com.jetbrains.python.errorProcessing.PyError;
import com.jetbrains.python.packaging.management.PythonPackagesInstaller;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.stream.Collectors;

public final class PyPackageManagerUI {
  private static final @NotNull Logger LOG = Logger.getInstance(PyPackageManagerUI.class);

  private final @Nullable Listener myListener;
  private final @NotNull Project myProject;
  private final @NotNull Sdk mySdk;

  public interface Listener {
    void started();

    void finished(List<ExecutionException> exceptions);
  }

  public PyPackageManagerUI(@NotNull Project project, @NotNull Sdk sdk, @Nullable Listener listener) {
    myProject = project;
    mySdk = sdk;
    myListener = listener;
  }

  public void install(final @Nullable List<PyRequirement> requirements, final @NotNull List<String> extraArgs) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC);
    ProgressManager.getInstance().run(new InstallTask(myProject, mySdk, requirements, extraArgs, myListener));
  }

  public void uninstall(final @NotNull List<PyPackage> packages) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC);
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

  private static Map<String, Set<PyPackage>> collectDependents(final @NotNull List<PyPackage> packages,
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

    protected final @NotNull Sdk mySdk;
    protected final @Nullable Listener myListener;

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

    protected abstract @NotNull List<ExecutionException> runTask(@NotNull ProgressIndicator indicator);

    protected abstract @NotNull @NlsContexts.NotificationTitle String getSuccessTitle();

    protected abstract @NotNull @NlsContexts.NotificationContent String getSuccessDescription();

    protected abstract @NotNull @NlsContexts.NotificationTitle String getFailureTitle();

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

    protected void taskFinished(final @NotNull List<ExecutionException> exceptions) {
      if (exceptions.isEmpty()) {
        sendNotification(
          getSuccessTitle(),
          getSuccessDescription(),
          NotificationType.INFORMATION,
          exceptions,
          null
        );
      }
      else {
        final List<Pair<String, String>> requirements =
          this instanceof InstallTask && ((InstallTask)this).myRequirements != null ? ContainerUtil.flatMap(
            ((InstallTask)this).myRequirements,
            req -> ContainerUtil.map(req.getInstallOptions(), option -> Pair.create(option, req.getName()))) : null;
        final List<String> packageManagerArguments = exceptions.stream()
          .flatMap(e -> {
            if (e instanceof PyExecutionException pyExecutionException) {
              PyError pyError = pyExecutionException.getPyError();
              if (pyError instanceof ExecError execError) {
                return Arrays.stream(execError.getExeAndArgs().getSecond());
              }
            }
            return null;
          })
          .toList();
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

          sendNotification(
            getFailureTitle(),
            content,
            NotificationType.ERROR,
            exceptions,
            listener
          );
        }
      }
    }

    private void sendNotification(
      @NlsSafe @NotNull String title,
      @NlsSafe @NotNull String content,
      @NotNull NotificationType type,
      List<ExecutionException> exceptions,
      @Nullable NotificationListener listener
    ) {
      ApplicationManager.getApplication().invokeLater(() -> {
        Notification notification = new PackagingNotification(
          PACKAGING_GROUP_ID,
          title,
          content,
          type,
          listener
        );

        notification.notify(myProject);

        if (myListener != null) {
          myListener.finished(exceptions);
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
    private final @Nullable List<PyRequirement> myRequirements;
    private final @NotNull List<String> myExtraArgs;

    InstallTask(@Nullable Project project,
                @NotNull Sdk sdk,
                @Nullable List<PyRequirement> requirements,
                @NotNull List<String> extraArgs,
                @Nullable Listener listener) {
      super(project, sdk, PyBundle.message("python.packaging.progress.title.installing.packages"), listener);
      myRequirements = requirements;
      myExtraArgs = extraArgs;
    }

    @Override
    protected @NotNull List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      if (myProject == null) {
        // FIXME: proper error
        return exceptions;
      }

      var result = PythonPackagesInstaller.Companion.installPackages(
        myProject,
        mySdk,
        myRequirements,
        myExtraArgs,
        indicator
      );

      // FIXME: use packaging tool window service for managing error dialog
      if (result != null) {
        exceptions.add(result);
      }

      return exceptions;
    }

    @Override
    protected @NotNull String getSuccessTitle() {
      return PyBundle.message("python.packaging.notification.title.packages.installed.successfully");
    }

    @Override
    protected @NotNull String getSuccessDescription() {
      return myRequirements != null
             ? PyBundle.message("python.packaging.notification.description.installed.packages",
                                PyPackageUtil.requirementsToString(myRequirements))
             : PyBundle.message("python.packaging.notification.description.installed.all.requirements");
    }

    @Override
    protected @NotNull String getFailureTitle() {
      return PyBundle.message("python.packaging.notification.title.install.packages.failed");
    }
  }

  private static class UninstallTask extends PackagingTask {
    private final @NotNull List<PyPackage> myPackages;

    UninstallTask(@Nullable Project project,
                  @NotNull Sdk sdk,
                  @Nullable Listener listener,
                  @NotNull List<PyPackage> packages) {
      super(project, sdk, PyBundle.message("python.packaging.progress.title.uninstalling.packages"), listener);
      myPackages = packages;
    }

    @Override
    protected @NotNull List<ExecutionException> runTask(@NotNull ProgressIndicator indicator) {
      final List<ExecutionException> exceptions = new ArrayList<>();
      if (myProject == null) {
        return exceptions;
      }

      var result = PythonPackagesInstaller.Companion.uninstallPackages(
        myProject,
        mySdk,
        myPackages,
        indicator
      );

      if (result != null) {
        exceptions.add(result);
      }

      return exceptions;
    }

    @Override
    protected @NotNull String getSuccessTitle() {
      return PyBundle.message("python.packaging.notification.title.packages.uninstalled.successfully");
    }

    @Override
    protected @NotNull String getSuccessDescription() {
      final String packagesString = StringUtil.join(myPackages, pkg -> "'" + pkg.getName() + "'", ", ");
      return PyBundle.message("python.packaging.notification.description.uninstalled.packages", packagesString);
    }

    @Override
    protected @NotNull String getFailureTitle() {
      return PyBundle.message("python.packaging.notification.title.uninstall.packages.failed");
    }
  }
}