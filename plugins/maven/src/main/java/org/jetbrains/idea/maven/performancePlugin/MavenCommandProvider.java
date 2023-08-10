package org.jetbrains.idea.maven.performancePlugin;

import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.CreateCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class MavenCommandProvider implements CommandProvider {
  @Override
  public @NotNull Map<String, CreateCommand> getCommands() {
    return Map.of(ImportMavenProjectCommand.PREFIX, ImportMavenProjectCommand::new);
  }
}
