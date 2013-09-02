package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.EventDispatcher;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;

public class IdeaConfigurationServerSettings {
  private String myUserName = null;
  private String myPassword = null;
  private String mySessionId = null;
  private final File mySettingsFile;
  public boolean REMEMBER_SETTINGS = false;
  public boolean DO_LOGIN = false;
  private boolean mySettingsAlreadySynchronized = false;
  private static final String REMEMBER_SETTINGS_ATTR = "rememberSettings";
  private static final String DO_LOGIN_ATTR = "doLogin";
  private static final String PASSWORD = "password";
  private static final String LOGIN = "login";
  private static final String SETTINGS_LOADED_ATTR = "settingsLoaded";

  private IdeaServerStatus myStatus = IdeaServerStatus.LOGGED_OUT;
  private final EventDispatcher<StatusListener> myDispatcher = EventDispatcher.create(StatusListener.class);

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
    mySettingsFile = new File(PathManager.getSystemPath(), "idea-server/server-credentials.xml");
  }

  public void save() {
    mySettingsFile.getParentFile().mkdirs();
    try {
      JDOMUtil.writeDocument(createCredentialsDocument(), mySettingsFile, "\n");
    }
    catch (IOException e) {
      //ignore
    }
  }

  public void loadCredentials() {
    if (mySettingsFile.isFile()) {
      try {
        Document document = JDOMUtil.loadDocument(mySettingsFile);
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
    return mySessionId;
  }

  public void update(final String login, final String password) {
    myUserName = login;
    myPassword = password;
  }

  public void updateSession(final String session) {
    mySessionId = session;
  }

  public IdeaServerStatus getStatus() {
    return myStatus;
  }

  public void setStatus(final IdeaServerStatus status) {
    if (myStatus != status) {
      myStatus = status;
      myDispatcher.getMulticaster().statusChanged(status);
    }
  }

  public boolean wereSettingsSynchronized() {
    return mySettingsAlreadySynchronized;
  }

  public void settingsWereSynchronized() {
    mySettingsAlreadySynchronized = true;
  }

  public void logout() {
    mySessionId = null;
    setStatus(IdeaServerStatus.LOGGED_OUT);
  }

  public void addStatusListener(final StatusListener statusListener) {
    myDispatcher.addListener(statusListener);
  }

  public void removeStatusListener(final StatusListener statusListener) {
    myDispatcher.removeListener(statusListener);
  }

  public HttpConfigurable getHttpProxySettings() {
    return myProxySettings;
  }
}
