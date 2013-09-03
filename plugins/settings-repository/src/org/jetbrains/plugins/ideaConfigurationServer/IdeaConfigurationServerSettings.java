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
  private static final String REMEMBER_SETTINGS_ATTR = "rememberSettings";
  private static final String DO_LOGIN_ATTR = "doLogin";
  private static final String PASSWORD = "password";
  private static final String LOGIN = "login";

  private String userName;
  private String password;
  private final File settingsFile;

  public boolean REMEMBER_SETTINGS ;
  public boolean DO_LOGIN;

  private IdeaConfigurationServerStatus status = IdeaConfigurationServerStatus.LOGGED_OUT;

  private final HttpConfigurable proxySettings = new HttpConfigurable();

  public IdeaConfigurationServerSettings() {
    settingsFile = new File(PathManager.getSystemPath(), "ideaConfigurationServer/state.xml");
  }

  private Document createCredentialsDocument() {
    Element user = new Element("user");
    if (userName != null) {
      user.setAttribute(LOGIN, userName);
    }
    if (password != null) {
      user.setAttribute(PASSWORD, PasswordUtil.encodePassword(password));
    }
    user.setAttribute(REMEMBER_SETTINGS_ATTR, String.valueOf(REMEMBER_SETTINGS));
    user.setAttribute(DO_LOGIN_ATTR, String.valueOf(DO_LOGIN));
    try {
      proxySettings.writeExternal(user);
    }
    catch (WriteExternalException e) {
      //ignore
    }
    return new Document(user);
  }

  public void save() {
    //noinspection ResultOfMethodCallIgnored
    settingsFile.getParentFile().mkdirs();
    try {
      JDOMUtil.writeDocument(createCredentialsDocument(), settingsFile, "\n");
    }
    catch (IOException e) {
      //ignore
    }
  }

  public void load() {
    if (settingsFile.isFile()) {
      try {
        Document document = JDOMUtil.loadDocument(settingsFile);
        Element element = document.getRootElement();
        if (element != null) {
          userName = element.getAttributeValue(LOGIN);
          password = element.getAttributeValue(PASSWORD);
          if (password != null) {
            password = PasswordUtil.decodePassword(password);
          }
          REMEMBER_SETTINGS = Boolean.valueOf(element.getAttributeValue(REMEMBER_SETTINGS_ATTR));
          DO_LOGIN = Boolean.valueOf(element.getAttributeValue(DO_LOGIN_ATTR));

          proxySettings.readExternal(element);
        }
      }
      catch (Exception e) {
        //ignore
      }
    }
  }


  public String getUserName() {
    return userName;
  }

  public String getPassword() {
    return password;
  }

  public void update(final String login, final String password) {
    userName = login;
    this.password = password;
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

  public void logout() {
    setStatus(IdeaConfigurationServerStatus.LOGGED_OUT);
  }

  public HttpConfigurable getHttpProxySettings() {
    return proxySettings;
  }
}