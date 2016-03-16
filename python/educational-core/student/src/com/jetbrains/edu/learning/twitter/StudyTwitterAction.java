package com.jetbrains.edu.learning.twitter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Action that provide tweeting functionality to plugin.
 * Is performed for every solved task and configured by StudyPluginConfigurator instance.
 * 
 * In order to provide tweeting functionality in your plugin you should override twitter 
 * methods in StudyPluginConfigurator instance of your plugin.
 */
public class StudyTwitterAction extends StudyAfterCheckAction {
  Logger LOG = Logger.getInstance(StudyTwitterAction.class);
  @Override
  public void run(@NotNull Project project, @NotNull Task solvedTask, StudyStatus statusBeforeCheck) {
    try {
      StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
      if (configurator == null) {
        LOG.warn("Plugin configurator not found");
        return;
      }
      
      if (configurator.askToTweet(project, solvedTask, statusBeforeCheck)) {
        boolean isAuthorized = !configurator.getTwitterAccessToken(project).isEmpty();
        Twitter twitter = StudyTwitterUtils.getTwitter(configurator.getConsumerKey(project), configurator.getConsumerSecret(project));
        StudyTwitterUtils.configureTwitter(twitter, project, isAuthorized);
        StudyTwitterUtils.TwitterDialogPanel panel = configurator.getTweetDialogPanel(solvedTask);
        if (panel != null) {
          StudyTwitterUtils.showPostTweetDialogAndPostTweet(twitter, panel);
        }
        else {
          LOG.warn("Plugin didn't provide twitter panel");          
        }
      } 
    }
    catch (TwitterException e) {
      LOG.warn(e.getMessage());
    }
  }
}
