package org.jetbrains.plugins.ruby.ruby.actions.setup;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.version.management.rbenv.gemsets.RbenvGemsetManager;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectRbenvSdkNotifiableActivityProvider extends SelectSdkNotifiableActivityProvider {
  private static final Pattern RBENV_USE_SDK_PATTERN = Pattern.compile("rbenv shell ([\\w-.]+)");

  @Override
  public boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern) {
    return RBENV_USE_SDK_PATTERN.matcher(pattern).matches();
  }

  @Nullable
  @Override
  Sdk findSdk(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    Matcher matcher = RBENV_USE_SDK_PATTERN.matcher(commandLine);
    String version = matcher.matches() ? matcher.group(1) : null;

    return RbenvGemsetManager.findRootRbenvSdk(Objects.requireNonNull(version));
  }
}