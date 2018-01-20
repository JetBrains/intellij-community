package org.jetbrains.plugins.ruby.ruby.actions.providers;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.rails.run.configuration.server.RailsServerRunConfiguration;
import org.jetbrains.plugins.ruby.rails.run.configuration.server.RailsServerRunConfigurationType;

import java.util.List;

import static org.jetbrains.plugins.ruby.rails.RailsConstants.*;
import static org.jetbrains.plugins.ruby.ruby.actions.providers.RailsServerRunAnythingProvider.State.*;

public class RailsServerRunAnythingProvider extends RubyRunAnythingProviderBase<RailsServerRunConfiguration> {
  @NotNull
  @Override
  String getExecCommand() {
    return "rails server";
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return RailsServerRunConfigurationType.getInstance().getConfigurationFactories()[0];
  }

  enum State {SERVER, PORT, IP, ENV}

  @Override
  void extendConfiguration(@NotNull RailsServerRunConfiguration configuration,
                           @NotNull VirtualFile baseDirectory,
                           @NotNull String commandLine) {
    List<String> serverOptions = ContainerUtil.newArrayList();

    State state = SERVER;
    for (String argument : StringUtil.split(getArguments(commandLine), " ")) {
      if (!StringUtil.startsWith(argument, "-")) {
        switch (state) {
          case SERVER:
            configuration.setServerType(argument);
            break;
          case PORT:
            configuration.setPort(argument);
            break;
          case IP:
            configuration.setIPAddr(argument);
            break;
          case ENV:
            configuration.setRailsEnvironmentType(argument);
            break;
        }

        continue;
      }

      if (argument.equals(PARAM_SERVER_PORT) || argument.equals("--port")) {
        state = PORT;
      }
      else if (argument.equals(PARAM_SERVER_IP) || argument.equals("--binding")) {
        state = IP;
      }
      else if (argument.equals(PARAM_SERVER_ENVIRONMENT) || argument.equals("--environment")) {
        state = ENV;
      }
      else if (argument.startsWith("-") && argument.length() > 1 || argument.startsWith("--") && argument.length() > 2) {
        serverOptions.add(argument);
      }
    }

    appendParameters(parameter -> configuration.setScriptArgs(parameter), () -> configuration.getScriptArgs(), serverOptions);
  }
}
