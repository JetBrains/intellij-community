package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.run.configuration.RubyRunConfigurationType;
import org.jetbrains.plugins.ruby.ruby.run.configuration.rubyScript.RubyRunConfiguration;

import java.util.List;

public class RubyRunAnythingProvider extends RubyRunAnythingProviderBase<RubyRunConfiguration> {
  private static final String PROGRAM_ARGUMENTS = "PROGRAM_ARGUMENTS";

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
    RubyParsedArguments arguments = parseRubyCommandString(baseDirectory, commandLine);

    configuration.setScriptPath(arguments.myPath);

    configuration.setRubyArgs(configuration.getRubyArgs() + StringUtil.join(arguments.myRubyOptions, " "));
    configuration.setScriptArgs(configuration.getScriptArgs() + StringUtil.join(arguments.myScriptArguments, " "));
  }

  @NotNull
  private RubyParsedArguments parseRubyCommandString(@NotNull VirtualFile baseDirectory, @NotNull String commandLine) {
    String argumentsString = getArguments(commandLine);

    List<String> rubyOptions = ContainerUtil.newArrayList();
    List<String> scriptArguments = ContainerUtil.newArrayList();
    String scriptPath = null;

    String state = null;
    VirtualFile path;
    for (String argument : StringUtil.split(argumentsString, " ")) {
      if (state == PROGRAM_ARGUMENTS) {
        scriptArguments.add(argument);
        continue;
      }

      if (argument.startsWith("-") && argument.length() > 1|| argument.startsWith("--") && argument.length() > 2) {
        rubyOptions.add(argument);
        continue;
      }

      path = baseDirectory.findFileByRelativePath(argument);
      if (path != null) {
         scriptPath = path.getPath();
        state = PROGRAM_ARGUMENTS;
      }
    }

    return new RubyParsedArguments(rubyOptions, scriptPath, scriptArguments);
  }

  static class RubyParsedArguments {
    private final List<String> myRubyOptions;
    private final String myPath;
    private final List<String> myScriptArguments;

    public RubyParsedArguments(List<String> rubyOptions, String path, List<String> scriptArguments) {
      myRubyOptions = rubyOptions;
      myPath = path;
      myScriptArguments = scriptArguments;
    }
  }
}