package com.intellij.util.net;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.application.ApplicationManager;
import org.jdom.Element;
import org.apache.xmlrpc.Base64;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 7, 2003
 * Time: 3:58:23 PM
 * To change this template use Options | File Templates.
 */
public class HttpConfigurable implements JDOMExternalizable, ApplicationComponent {
  public boolean USE_HTTP_PROXY = false;
  public String PROXY_HOST = "";
  public int PROXY_PORT = 80;

  public boolean PROXY_AUTHENTICATION = false;
  public String PROXY_LOGIN = "";
  public String PROXY_PASSWORD_CRYPT = "";
  public boolean KEEP_PROXY_PASSWORD = false;

  public static HttpConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(HttpConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (!KEEP_PROXY_PASSWORD)
      PROXY_PASSWORD_CRYPT = "";
  }

  public void writeExternal(Element element) throws WriteExternalException {
    String proxyPassword = PROXY_PASSWORD_CRYPT;
    if (!KEEP_PROXY_PASSWORD)
      PROXY_PASSWORD_CRYPT = "";

    DefaultJDOMExternalizer.writeExternal(this, element);

    PROXY_PASSWORD_CRYPT = proxyPassword;
  }

  public String getComponentName() {
    return "HttpConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getPlainProxyPassword () {
    return new String(Base64.decode(HttpConfigurable.getInstance().PROXY_PASSWORD_CRYPT.getBytes()));
  }

  public void setPlainProxyPassword (String password) {
    PROXY_PASSWORD_CRYPT = new String(Base64.encode(new String(password).getBytes()));
  }

  private Authenticator getAuthenticator () {
    return new Authenticator () {
      protected PasswordAuthentication getPasswordAuthentication() {
        if (PROXY_AUTHENTICATION &&
            ! KEEP_PROXY_PASSWORD) {
          AuthenticationDialog dlg = new AuthenticationDialog(getRequestingHost(), getRequestingPrompt());
          dlg.show();
        }
        return new PasswordAuthentication(PROXY_LOGIN,
                                          getPlainProxyPassword().toCharArray());
      }
    };
  }

  // @todo [all] Call this function before every HTTP connection.
  /**
   * Call this function before every HTTP connection.
   * If system configured to use HTTP proxy, this function
   * checks all required parameters and ask password if
   * required.
   * @param url URL for HTTP connection
   * @throws IOException
   */
  public void prepareURL (String url) throws IOException {
    if (USE_HTTP_PROXY) {
      System.setProperty("proxySet", "true");
      System.setProperty("http.proxyHost", PROXY_HOST);
      System.setProperty("http.proxyPort", Integer.toString (PROXY_PORT));
      Authenticator.setDefault(getAuthenticator());
    } else {
      System.setProperty("proxySet", "false");
      Authenticator.setDefault(null);
    }

    HttpURLConnection connection = (HttpURLConnection)new URL (url).openConnection();
    connection.connect();
    connection.getInputStream();
    connection.disconnect();
  }
}
