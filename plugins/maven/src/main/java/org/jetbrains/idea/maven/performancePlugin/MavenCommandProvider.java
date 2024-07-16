package org.jetbrains.idea.maven.performancePlugin;

import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.CreateCommand;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class MavenCommandProvider implements CommandProvider {
  @Override
  public @NotNull Map<String, CreateCommand> getCommands() {
    return new HashMap<>() {{
      put(ImportMavenProjectCommand.PREFIX, ImportMavenProjectCommand::new);
      put(SetMavenSettingsXmlFilePathCommand.PREFIX, SetMavenSettingsXmlFilePathCommand::new);
      put(ExecuteMavenGoalCommand.PREFIX, ExecuteMavenGoalCommand::new);
      put(LinkMavenProjectCommand.PREFIX, LinkMavenProjectCommand::new);
      put(UnlinkMavenProjectCommand.PREFIX, UnlinkMavenProjectCommand::new);
      put(ToggleMavenProfilesCommand.PREFIX, ToggleMavenProfilesCommand::new);
      put(DownloadMavenArtifactsCommand.PREFIX, DownloadMavenArtifactsCommand::new);
      put(CreateMavenProjectCommand.PREFIX, CreateMavenProjectCommand::new);
      put(UpdateMavenGoalCommand.PREFIX, UpdateMavenGoalCommand::new);
      put(ValidateMavenGoalCommand.PREFIX, ValidateMavenGoalCommand::new);
      put(UpdateMavenFoldersCommand.PREFIX, UpdateMavenFoldersCommand::new);
      put(MavenIndexUpdateCommand.PREFIX, MavenIndexUpdateCommand::new);
      put(CheckIfMavenIndexesHaveArtefactCommand.PREFIX, CheckIfMavenIndexesHaveArtefactCommand::new);
      put(SetMavenDelegatedBuildCommand.PREFIX, SetMavenDelegatedBuildCommand::new);
      put(RefreshMavenProjectCommand.PREFIX, RefreshMavenProjectCommand::new);
    }};
  }
}
