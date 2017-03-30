package com.jetbrains.edu.learning;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.twitter.StudyTwitterUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides twitting to plugin<br>
 * Example can be found in
 * <a href="https://github.com/JetBrains/educational-plugins/blob/master/Edu-Utils/Edu-Kotlin/src/com/jetbrains/edu/kotlin/EduKotlinTwitterConfigurator.java">Edu Kotlin</a> plugin
 * @see com.jetbrains.edu.learning.twitter.StudyTwitterAction
 * */
public interface StudyTwitterPluginConfigurator {
  ExtensionPointName<StudyTwitterPluginConfigurator> EP_NAME = ExtensionPointName.create("Edu.studyTwitterPluginConfigurator");

  /**
   * To implement tweeting you should register you app in twitter. For registered application twitter provide
   * consumer key and consumer secret, that are used for authorize by OAuth.
   * @return consumer key for current educational plugin
   */
  @NotNull
  String getConsumerKey(@NotNull final Project project);

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
  
  void setAskToTweet(@NotNull Project project, boolean askToTweet);

  boolean accept(@NotNull final Project project);
}
