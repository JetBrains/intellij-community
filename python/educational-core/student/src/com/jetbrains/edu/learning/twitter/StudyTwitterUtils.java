package com.jetbrains.edu.learning.twitter;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.jetbrains.edu.learning.StudyTwitterPluginConfigurator;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.apache.http.HttpStatus;
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
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class StudyTwitterUtils {
  private static final Logger LOG = Logger.getInstance(StudyTwitterUtils.class);
  
  /**
   * Set consumer key and secret. 
   * @return Twitter instance with consumer key and secret set.
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
   */
  private static void setAuthInfoInTwitter(Twitter twitter, @NotNull String accessToken,
                                           @NotNull String tokenSecret) {
    AccessToken token = new AccessToken(accessToken, tokenSecret);
    twitter.setOAuthAccessToken(token);
  }

  public static void createTwitterDialogAndShow(@NotNull Project project, 
                                                @NotNull final StudyTwitterPluginConfigurator configurator,
                                                @NotNull Task task) {
    ApplicationManager.getApplication().invokeLater(() -> {
      DialogWrapper.DoNotAskOption doNotAskOption = createDoNotAskOption(project, configurator);
      StudyTwitterUtils.TwitterDialogPanel panel = configurator.getTweetDialogPanel(task);
      if (panel != null) {
        TwitterDialogWrapper wrapper = new TwitterDialogWrapper(project, panel, doNotAskOption);
        wrapper.setDoNotAskOption(doNotAskOption);
        panel.addTextFieldVerifier(createTextFieldLengthDocumentListener(wrapper, panel));

        if (wrapper.showAndGet()) {
          try {
            boolean isAuthorized = !configurator.getTwitterAccessToken(project).isEmpty();
            Twitter twitter = getTwitter(configurator.getConsumerKey(project), configurator.getConsumerSecret(project));
            if (!isAuthorized) {
              authorizeAndUpdateStatus(project, twitter, panel);
            }
            else {
              setAuthInfoInTwitter(twitter, configurator.getTwitterAccessToken(project), configurator.getTwitterTokenSecret(project));
              updateStatus(panel, twitter);
            }
          }
          catch (TwitterException | IOException e) {
            LOG.warn(e.getMessage());
            Messages.showErrorDialog("Status wasn\'t updated. Please, check internet connection and try again", "Twitter");
          }
        }
        else {
          LOG.warn("Panel is null");
        }
      }
    });
  }


  private static DialogWrapper.DoNotAskOption createDoNotAskOption(@NotNull final Project project,
                                                                   @NotNull final StudyTwitterPluginConfigurator configurator) {
    return new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return true;
      }

      @Override
      public void setToBeShown(boolean toBeShown, int exitCode) {
        if (exitCode == DialogWrapper.CANCEL_EXIT_CODE || exitCode == DialogWrapper.OK_EXIT_CODE) {
          configurator.setAskToTweet(project, toBeShown);
        }
      }

      @Override
      public boolean canBeHidden() {
        return true;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return true;
      }

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return "Never ask me to tweet";
      }
    };
  }

  /**
   * Post on twitter media and text from panel
   * @param panel shown to user and used to provide data to post 
   */
  public static void updateStatus(StudyTwitterUtils.TwitterDialogPanel panel, Twitter twitter) throws IOException, TwitterException {
    StatusUpdate update = new StatusUpdate(panel.getMessage());
    InputStream e = panel.getMediaSource();
    if (e != null) {
      File imageFile = FileUtil.createTempFile("twitter_media", panel.getMediaExtension());
      FileUtil.copy(e, new FileOutputStream(imageFile));
      update.media(imageFile);
    }

    twitter.updateStatus(update);
    BrowserUtil.browse("https://twitter.com/");
  }

  /**
   * Show twitter dialog, asking user to tweet about his achievements. Post tweet with provided by panel
   * media and text. 
   * As a result of succeeded tweet twitter website is opened in default browser.
   */
  public static void authorizeAndUpdateStatus(@NotNull final Project project, @NotNull final Twitter twitter,
                                              @NotNull final StudyTwitterUtils.TwitterDialogPanel panel) throws TwitterException {
    RequestToken requestToken = twitter.getOAuthRequestToken();
    BrowserUtil.browse(requestToken.getAuthorizationURL());

    ApplicationManager.getApplication().invokeLater(() -> {
      String pin = createAndShowPinDialog();
      if (pin != null) {
        try {
          AccessToken token = twitter.getOAuthAccessToken(requestToken, pin);
          StudyTwitterPluginConfigurator configurator = StudyUtils.getTwitterConfigurator(project);
          if (configurator != null) {
            configurator.storeTwitterTokens(project, token.getToken(), token.getTokenSecret());
            updateStatus(panel, twitter);
          }
          else {
            LOG.warn("No twitter configurator is provided for the plugin");
          }
        }
        catch (TwitterException e) {
          if (e.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            LOG.warn("Unable to get the access token.");
            LOG.warn(e.getMessage());
          }
        }
        catch (IOException e) {
          LOG.warn(e.getMessage());
        }
      }
    });
  }

  public static String createAndShowPinDialog() {
    return Messages.showInputDialog("Twitter PIN:", "Twitter Authorization", null, "", new InputValidatorEx() {
      @Nullable
      @Override
      public String getErrorText(String inputString) {
        inputString = inputString.trim();
        if (inputString.isEmpty()) {
          return "PIN shouldn't be empty.";
        }
        if (!isNumeric(inputString)) {
          return "PIN should be numeric.";
        }
        return null;
      }

      @Override
      public boolean checkInput(String inputString) {
        return getErrorText(inputString) == null;
      }

      @Override
      public boolean canClose(String inputString) {
        return true;
      }
      
      private boolean isNumeric(@NotNull final String string) {
        for (char c: string.toCharArray()) {
          if (!StringUtil.isDecimalDigit(c)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  /**
   * Listener updates label indicating remaining symbols number like in twitter.
   */
  private static DocumentListener createTextFieldLengthDocumentListener(@NotNull TwitterDialogWrapper builder, @NotNull final StudyTwitterUtils.TwitterDialogPanel panel) {
    return new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        int length = e.getDocument().getLength();
        if (length > 140 || length == 0) {
          builder.setOKActionEnabled(false);
          panel.getRemainSymbolsLabel().setText("<html><font color='red'>" + String.valueOf(140 - length) + "</font></html>");
        } else {
          builder.setOKActionEnabled(true);
          panel.getRemainSymbolsLabel().setText(String.valueOf(140 - length));
        }

      }
    };
  }

  /**
   * Dialog wrapper class with DoNotAsl option for asking user to tweet.
   * */
  private static class TwitterDialogWrapper extends DialogWrapper {
    private final StudyTwitterUtils.TwitterDialogPanel myPanel;

    TwitterDialogWrapper(@Nullable Project project, @NotNull StudyTwitterUtils.TwitterDialogPanel panel, DoNotAskOption doNotAskOption) {
      super(project);
      setTitle("Twitter");
      setDoNotAskOption(doNotAskOption);
      setOKButtonText("Tweet");
      setCancelButtonText("No");
      setResizable(true);
      Dimension preferredSize = panel.getPreferredSize();
      setSize((int) preferredSize.getHeight(), (int) preferredSize.getWidth());
      myPanel = panel;
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return new JBScrollPane(myPanel);
    }
  }

  /**
   * Class provides structure for twitter dialog panel
   */
  public abstract static class TwitterDialogPanel extends JPanel {

    /**
     * Provides tweet text
     */
    @NotNull public abstract String getMessage();

    /**
     * @return Input stream of media should be posted or null if there's nothing to post 
     */
    @Nullable public abstract InputStream getMediaSource();
    
    @Nullable public abstract String getMediaExtension();

    /**
     * @return label that will be used to show remained symbol number
     */
    @NotNull public abstract JLabel getRemainSymbolsLabel();

    /**
     * Api to add document listener to field containing tweet text
     */
    public abstract void addTextFieldVerifier(@NotNull final DocumentListener documentListener);
    
  }
}
