package org.jetbrains.idea.maven.performancePlugin;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * The command sets a maven user settings.xml file path
 * Syntax: %setUserSettingsFilePath [path]
 * Example: %setUserSettingsFilePath /tmp/.m3/settings.xml
 */
public class SetMavenSettingsXmlFilePathCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "setMavenSettingsXmlFilePath";

  public SetMavenSettingsXmlFilePathCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    String path = extractCommandArgument(PREFIX).split(" ")[0];
    var settings = MavenProjectsManager.getInstance(context.getProject()).getGeneralSettings();
    var promise = new AsyncPromise<>();
    settings.addListener(() -> {
      promise.setResult(null);
    });
    settings.setUserSettingsFile(path);

    return promise;
  }
}
