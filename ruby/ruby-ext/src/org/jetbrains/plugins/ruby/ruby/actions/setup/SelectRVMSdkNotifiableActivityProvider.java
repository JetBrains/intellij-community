package org.jetbrains.plugins.ruby.ruby.actions.setup;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.rvm.RVMSupportUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectRVMSdkNotifiableActivityProvider extends SelectSdkNotifiableActivityProvider {
  private static final Pattern RVM_USE_SDK_PATTERN = Pattern.compile("rvm use ([\\w-.]+)(?:@([\\w-.]+))?");

  @Override
  public boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern) {
    return RVM_USE_SDK_PATTERN.matcher(pattern).matches();
  }

  @Nullable
  @Override
  Sdk findSdk(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    Matcher matcher = RVM_USE_SDK_PATTERN.matcher(commandLine);

    String version = null;
    String gemset = null;
    if (matcher.matches()) {
      version = matcher.group(1);
      gemset = matcher.group(2);
    }
    assert version != null;

    if (!version.startsWith("ruby")) {
      version = "ruby-" + version;
    }

    return RVMSupportUtil.findSdkBy(version, gemset);
  }
}