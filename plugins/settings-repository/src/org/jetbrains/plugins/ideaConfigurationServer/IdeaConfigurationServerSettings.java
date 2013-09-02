package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;

public class IdeaConfigurationServerSettings {
  private String myUserName = null;
  private String myPassword = null;
  private String sessionId = null;
  private final File settingsFile;

  public boolean REMEMBER_SETTINGS = false;
  public boolean DO_LOGIN = false;
  private boolean mySettingsAlreadySynchronized = false;

  private static final String REMEMBER_SETTINGS_ATTR = "rememberSettings";
  private static final String DO_LOGIN_ATTR = "doLogin";
  private static final String PASSWORD = "password";
  private static final String LOGIN = "login";
  private static final String SETTINGS_LOADED_ATTR = "settingsLoaded";

  private IdeaConfigurationServerStatus status = IdeaConfigurationServerStatus.LOGGED_OUT;

  private final HttpConfigurable myProxySettings = new HttpConfigurable();

  private Document createCredentialsDocument() {
    Element user = new Element("user");
    if (myUserName != null) user.setAttribute(LOGIN, myUserName);
    if (myPassword != null) user.setAttribute(PASSWORD, PasswordUtil.encodePassword(myPassword));
    user.setAttribute(REMEMBER_SETTINGS_ATTR, String.valueOf(REMEMBER_SETTINGS));
    user.setAttribute(DO_LOGIN_ATTR, String.valueOf(DO_LOGIN));
    user.setAttribute(SETTINGS_LOADED_ATTR, String.valueOf(mySettingsAlreadySynchronized));
    try {
      myProxySettings.writeExternal(user);
    }
    catch (WriteExternalException e) {
      //ignore
    }
    return new Document(user);
  }


  public IdeaConfigurationServerSettings() {
    settingsFile = new File(PathManager.getSystemPath(), "idea-server/server-credentials.xml");
  }

  public void save() {
    settingsFile.getParentFile().mkdirs();
    try {
      JDOMUtil.writeDocument(createCredentialsDocument(), settingsFile, "\n");
    }
    catch (IOException e) {
      //ignore
    }
  }

  public void loadCredentials() {
    if (settingsFile.isFile()) {
      try {
        Document document = JDOMUtil.loadDocument(settingsFile);
        Element element = document.getRootElement();
        if (element != null) {
          myUserName = element.getAttributeValue(LOGIN);
          myPassword = element.getAttributeValue(PASSWORD);
          mySettingsAlreadySynchronized = Boolean.valueOf(element.getAttributeValue(SETTINGS_LOADED_ATTR));
          if (myPassword != null) {
            myPassword = PasswordUtil.decodePassword(myPassword);
          }
          REMEMBER_SETTINGS = Boolean.valueOf(element.getAttributeValue(REMEMBER_SETTINGS_ATTR));
          DO_LOGIN = Boolean.valueOf(element.getAttributeValue(DO_LOGIN_ATTR));

          myProxySettings.readExternal(element);
        }
      }
      catch (Exception e) {
        //ignore
      }
    }
  }


  public String getUserName() {
    return myUserName;
  }

  public String getPassword() {
    return myPassword;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void update(final String login, final String password) {
    myUserName = login;
    myPassword = password;
  }

  public void updateSession(final String session) {
    sessionId = session;
  }

  public IdeaConfigurationServerStatus getStatus() {
    return status;
  }

  public void setStatus(IdeaConfigurationServerStatus status) {
    if (this.status != status) {
      this.status = status;
      ApplicationManager.getApplication().getMessageBus().syncPublisher(StatusListener.TOPIC).statusChanged(status);
    }
  }

  public boolean wereSettingsSynchronized() {
    return mySettingsAlreadySynchronized;
  }

  public void settingsWereSynchronized() {
    mySettingsAlreadySynchronized = true;
  }

  public void logout() {
    sessionId = null;
    setStatus(IdeaConfigurationServerStatus.LOGGED_OUT);
  }

  public HttpConfigurable getHttpProxySettings() {
    return myProxySettings;
  }
}