package org.jetbrains.plugins.ruby.ruby.actions.setup;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;

import java.util.Objects;

public abstract class SelectSdkNotifiableActivityProvider extends RunAnythingNotifiableActivityProviderBase {
  private Sdk oldSdk;

  @Nullable
  abstract Sdk findSdk(@NotNull Project project, @Nullable Module module, @NotNull String commandLine);

  @Override
  public boolean runNotificationProduceActivity(@NotNull DataContext dataContext, @NotNull String pattern) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) {
      return false;
    }

    Sdk sdk = findSdk(Objects.requireNonNull(project), module, pattern);
    if (sdk == null) {
      return false;
    }

    oldSdk = RModuleUtil.getInstance().findRubySdkForModule(module);

    return RModuleUtil.getInstance().changeModuleSdk(sdk, module);
  }

  @Override
  protected Runnable getRollbackAction(@NotNull DataContext dataContext) {
    Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) {
      return null;
    }

    return () -> RModuleUtil.getInstance().changeModuleSdk(oldSdk, module);
  }

  @NotNull
  @Override
  protected String getNotificationTitle(@NotNull DataContext dataContext, @NotNull String pattern) {
    return RBundle.message("run.anything.custom.activity.sdk.title");
  }

  @NotNull
  @Override
  protected String getNotificationContent(@NotNull DataContext dataContext, @NotNull String pattern) {
    Module module = LangDataKeys.MODULE.getData(dataContext);

    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(module);
    return sdk != null
           ? RBundle.message("run.anything.custom.activity.sdk.message", sdk.getName())
           : RBundle.message("run.anything.custom.activity.no.sdk");
  }
}