package com.jetbrains.edu.learning.twitter;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class StudyTwitterUtils {
  private static final Logger LOG = Logger.getInstance(StudyTwitterUtils.class);

  /**
   * Configure twitter instance: authorize if needed or set access token and token secret provided by configurator.
   * @param twitter
   * @param project
   * @param isAuthorized
   * @throws TwitterException
   */
  public static void configureTwitter(@NotNull final Twitter twitter, @NotNull final Project project,
                                         final boolean isAuthorized) throws TwitterException {
    if (!isAuthorized) {
      authorize(project, twitter);
    }
    else {
      StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
      if (configurator != null) {
        getTwitterForAuthorizedApp(twitter, configurator.getTwitterAccessToken(project), configurator.getTwitterTokenSecret(project));
      }
    }
  }

  /**
   * Set consumer key and secret. 
   * @param consumerKey
   * @param consumerSecret
   * @return
   */
  @NotNull
  public static Twitter getTwitter(@NotNull final String consumerKey, @NotNull final String consumerSecret) {
    ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
    configurationBuilder.setOAuthConsumerKey(consumerKey);
    configurationBuilder.setOAuthConsumerSecret(consumerSecret);
    return new TwitterFactory(configurationBuilder.build()).getInstance();
  }

  /**
   * Set access token and token secret in Twitter instance
   * @param twitter
   * @param accessToken
   * @param tokenSecret
   */
  private static void getTwitterForAuthorizedApp(Twitter twitter, @NotNull String accessToken,
                                                @NotNull String tokenSecret) {
    AccessToken token = new AccessToken(accessToken, tokenSecret);
    twitter.setOAuthAccessToken(token);
  }

  /**
   * Authorize user and save tokens by StudyPluginConfigurator#storeTwitterTokens
   * @param project
   * @param twitter
   * @throws TwitterException
   */
  public static void authorize(@NotNull final Project project, @NotNull final Twitter twitter) throws TwitterException {
    RequestToken requestToken = twitter.getOAuthRequestToken();
    BrowserUtil.browse(requestToken.getAuthorizationURL());

    ApplicationManager.getApplication().invokeLater(() -> {
      String pin = Messages.showInputDialog("Twitter PIN:", "Twitter Authorization", null, "", null);
      try {
        AccessToken token;
        if (pin != null && pin.length() > 0) {
          token = twitter.getOAuthAccessToken(requestToken, pin);
        }
        else {
          token = twitter.getOAuthAccessToken();
        }
        StudyPluginConfigurator configurator = StudyUtils.getConfigurator(project);
        if (configurator != null) {
          configurator.storeTwitterTokens(project, token.getToken(), token.getTokenSecret());
        }
        else {
          LOG.warn("Plugin configurator not found");
        }
      }
      catch (TwitterException e) {
        if (401 == e.getStatusCode()) {
          LOG.warn("Unable to get the access token.");
        }
        else {
          LOG.warn(e.getMessage());
        }
      }
    });
  }

  /**
   * Show twitter dialog, asking user to tweet about his achievements. Post tweet with provided by panel
   * media and text. 
   * As a result of succeeded tweet twitter website is opened in default browser.
   * @param twitter 
   * @param twitterDialogPanel 
   */
  public static void showPostTweetDialogAndPostTweet(@NotNull Twitter twitter, @NotNull final TwitterDialogPanel twitterDialogPanel) {
    ApplicationManager.getApplication().invokeLater(() -> {
      DialogBuilder builder = new DialogBuilder();
      twitterDialogPanel.addTextFieldVerifier(createTextFieldLengthDocumentListener(builder, twitterDialogPanel));
      builder.title("Twitter");
      builder.addOkAction().setText("Tweet");
      builder.addCancelAction();
      builder.setCenterPanel(new JBScrollPane(twitterDialogPanel));
      builder.resizable(true);
      if (builder.showAndGet()) {
        StatusUpdate update = new StatusUpdate(twitterDialogPanel.getMessage());
        try {
          InputStream inputStream = twitterDialogPanel.getMediaSource();
          if (inputStream != null) {
            File imageFile = FileUtil.createTempFile("twitter_media", "gif");
            
            FileUtil.copy(inputStream, new FileOutputStream(imageFile));
            update.media(imageFile);
          }
          twitter.updateStatus(update);
          BrowserUtil.browse("https://twitter.com/");
        }
        catch (IOException | TwitterException e) {
          LOG.warn(e.getMessage());
          Messages.showErrorDialog("Status wasn't updated. Please, check internet connection and try again", "Twitter");
        }
      }
    });
  }

  /**
   * Listener updates label indicating remaining symbols number like in twitter.
   * @param builder
   * @param panel
   * @return
   */
  public static DocumentListener createTextFieldLengthDocumentListener(@NotNull DialogBuilder builder, @NotNull final TwitterDialogPanel panel) {
    return new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        int length = e.getDocument().getLength();
        if (length > 140 || length == 0) {
          builder.setOkActionEnabled(false);
          panel.getRemainSymbolsLabel().setText("<html><font color='red'>" + String.valueOf(140 - length) + "</font></html>");
        }
        else {
          builder.setOkActionEnabled(true);
          panel.getRemainSymbolsLabel().setText(String.valueOf(140 - length));
        }
        
      }
    };
  }

  /**
   * Class provides structure for twitter dialog panel
   */
  public abstract static class TwitterDialogPanel extends JPanel {

    /**
     * Provides tweet text
     * @return 
     */
    @NotNull public abstract String getMessage();

    /**
     * 
     * @return Input stream of media should be posted or null if there's nothing to post 
     */
    @Nullable public abstract InputStream getMediaSource();

    /**
     * 
     * @return label that will be used to show remained symbol number
     */
    @NotNull public abstract JLabel getRemainSymbolsLabel();

    /**
     * Api to add document listener to field containing tweet text
     * @param documentListener
     */
    public abstract void addTextFieldVerifier(@NotNull final DocumentListener documentListener);
    
  }
}
