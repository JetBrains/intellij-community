package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyRunConfigurationType;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfiguration;

import java.util.List;

public class RubyRunAnythingProvider extends RubyRunAnythingProviderBase<RubyRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "ruby";
  }

  @NotNull
  @Override
  public RubyRunConfigurationType.RubyRunConfigurationFactory getConfigurationFactory() {
    return RubyRunConfigurationType.getInstance().getRubyScriptFactory();
  }

  @Override
  void extendConfiguration(@NotNull RubyRunConfiguration configuration,
                           @NotNull VirtualFile baseDirectory,
                           @NotNull String commandLine) {
    List<String> rubyOptions = ContainerUtil.newArrayList();
    List<String> scriptArguments = ContainerUtil.newArrayList();

    boolean isProgramParameters = false;
    for (String argument : getArguments(commandLine)) {
      if (isProgramParameters) {
        scriptArguments.add(argument);
        continue;
      }

      if (argument.startsWith("-") && argument.length() > 1) {
        rubyOptions.add(argument);
        continue;
      }

      VirtualFile file = baseDirectory.findChild(argument);
      configuration.setScriptPath(file != null ? file.getCanonicalPath() : "");

      isProgramParameters = true;
    }

    appendParameters(parameter -> configuration.setRubyArgs(parameter), () -> configuration.getRubyArgs(), rubyOptions);
    appendParameters(parameter -> configuration.setScriptArgs(parameter), () -> configuration.getScriptArgs(), scriptArguments);
  }
}