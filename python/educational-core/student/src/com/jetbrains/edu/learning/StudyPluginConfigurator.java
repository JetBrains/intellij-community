package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.settings.ModifiableSettingsPanel;
import com.jetbrains.edu.learning.twitter.StudyTwitterUtils;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public interface StudyPluginConfigurator {
  ExtensionPointName<StudyPluginConfigurator> EP_NAME = ExtensionPointName.create("Edu.studyPluginConfigurator");

  /**
   * Provide action group that should be placed on the tool window toolbar.
   * @return
   */
  @NotNull
  DefaultActionGroup getActionGroup(Project project);

  /**
   * Provide panels, that could be added to Task tool window.
   * @return Map from panel id, i.e. "Task description", to panel itself.
   */
  @NotNull
  Map<String, JPanel> getAdditionalPanels(Project project);

  @NotNull
  FileEditorManagerListener getFileEditorManagerListener(@NotNull final Project project, @NotNull final StudyToolWindow toolWindow);

  /**
   *
   * @return parameter for CodeMirror script. Available languages: @see <@linktourl http://codemirror.net/mode/>
   */
  @NotNull String getDefaultHighlightingMode();

  @Nullable
  StudyAfterCheckAction[] getAfterCheckActions();
  
  @NotNull String getLanguageScriptUrl();

  @Nullable
  ModifiableSettingsPanel getSettingsPanel();

  /**
   * To implement tweeting you should register you app in twitter. For registered application twitter provide
   * consumer key and consumer secret, that are used for authorize by OAuth.
   * @return consumer key for current educational plugin
   */
  @NotNull String getConsumerKey(@NotNull final Project project);

  /**
   * To implement tweeting you should register you app in twitter. For registered application twitter provide
   * consumer key and consumer secret, that are used for authorize by OAuth.
   * @return consumer secret for current educational plugin
   */
  @NotNull String getConsumerSecret(@NotNull final Project project);

  /**
   * The plugin implemented tweeting should define policy when user will be asked to tweet.
   *@param statusBeforeCheck @return 
   */
  boolean askToTweet(@NotNull final Project project, Task solvedTask, StudyStatus statusBeforeCheck);
  
  /**
   * Stores access token and token secret, obtained by authorizing PyCharm.
   */
  void storeTwitterTokens(@NotNull final Project project, @NotNull final String accessToken, @NotNull final String tokenSecret);

  /**
   * @return stored access token
   */
  @NotNull String getTwitterAccessToken(@NotNull Project project);

  /**
   * @return stored token secret
   */
  @NotNull String getTwitterTokenSecret(@NotNull Project project);

  /**
   * @return panel that will be shown to user in ask to tweet dialog. 
   */
  @Nullable
  StudyTwitterUtils.TwitterDialogPanel getTweetDialogPanel(@NotNull Task solvedTask);
  
  boolean accept(@NotNull final Project project);
}
