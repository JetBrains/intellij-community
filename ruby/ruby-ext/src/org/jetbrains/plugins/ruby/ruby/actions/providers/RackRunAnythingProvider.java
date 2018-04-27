package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.rack.run.RackRunConfiguration;
import org.jetbrains.plugins.ruby.rack.run.RackRunConfigurationType;

import static org.jetbrains.plugins.ruby.ruby.actions.providers.RackRunAnythingProvider.State.*;


public class RackRunAnythingProvider extends RubyRunAnythingProviderBase<RackRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "rackup";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RackRunConfigurationType.getInstance().getFactory();
  }

  enum State {SERVER, PORT, HOST, CONFIG}

  @Override
  void extendConfiguration(@NotNull RackRunConfiguration configuration, @NotNull VirtualFile baseDirectory, @NotNull String commandLine) {
    State state = CONFIG;
    for (String argument : getArguments(commandLine)) {
      if (!StringUtil.startsWith(argument, "-")) {
        switch (state) {
          case SERVER:
            configuration.setServer(argument);
            break;
          case PORT:
            configuration.setPort(argument);
            break;
          case HOST:
            configuration.setHost(argument);
            break;
          case CONFIG: {
            configuration.setConfig(argument);
            break;
          }
        }

        continue;
      }

      if (argument.equals("-p") || argument.equals("--port")) {
        state = PORT;
      }
      else if (argument.equals("-o") || argument.equals("--host")) {
        state = HOST;
      }
      else if (argument.equals("-s") || argument.equals("--server")) {
        state = SERVER;
      }
    }
  }
}