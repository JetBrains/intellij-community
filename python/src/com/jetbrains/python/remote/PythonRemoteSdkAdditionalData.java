package com.jetbrains.python.remote;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PythonRemoteSdkAdditionalData extends PythonSdkAdditionalData implements PyRemoteInterpreterSettings {
  public static final String SSH_PREFIX = "ssh://";


  private static final String HOST = "HOST";
  private static final String PORT = "PORT";
  private static final String ANONYMOUS = "ANONYMOUS";
  private static final String USERNAME = "USERNAME";
  private static final String PASSWORD = "PASSWORD";

  private static final String USE_KEY_PAIR = "USE_KEY_PAIR";
  private static final String PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";
  private static final String KNOWN_HOSTS_FILE = "MY_KNOWN_HOSTS_FILE";
  private static final String PASSPHRASE = "PASSPHRASE";


  private static final String INTERPRETER_PATH = "INTERPRETER_PATH";


  private String myHost;
  private int myPort;
  private boolean myAnonymous;
  private String myUserName;
  private String myPassword;
  private boolean myUseKeyPair;
  private String myPrivateKeyFile;
  private String myKnownHostsFile;
  private String myPassphrase;
  private boolean myStorePassword;
  private boolean myStorePassphrase;

  private String myInterpreterPath;

  public PythonRemoteSdkAdditionalData(@Nullable PythonSdkFlavor flavor) {
    super(flavor);
  }

  public String getInterpreterPath() {
    return myInterpreterPath;
  }

  public void setInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
  }


  public String getFullInterpreterPath() {
    return SSH_PREFIX + myUserName + "@" + myHost + ":" + myInterpreterPath;
  }

  @Override
  public void save(@NotNull final Element rootElement) {
    super.save(rootElement);

    rootElement.setAttribute(HOST, getHost());
    rootElement.setAttribute(PORT, Integer.toString(getPort()));
    rootElement.setAttribute(ANONYMOUS, Boolean.toString(isAnonymous()));
    rootElement.setAttribute(USERNAME, getSerializedUserName());
    rootElement.setAttribute(PASSWORD, getSerializedPassword());
    rootElement.setAttribute(PRIVATE_KEY_FILE, StringUtil.notNullize(getPrivateKeyFile()));
    rootElement.setAttribute(KNOWN_HOSTS_FILE, StringUtil.notNullize(getKnownHostsFile()));
    rootElement.setAttribute(PASSPHRASE, getSerializedPassphrase());
    rootElement.setAttribute(USE_KEY_PAIR, Boolean.toString(isUseKeyPair()));

    rootElement.setAttribute(INTERPRETER_PATH, getInterpreterPath());
  }


  public Object clone() throws CloneNotSupportedException {
    try {
      final PythonRemoteSdkAdditionalData copy = (PythonRemoteSdkAdditionalData)super.clone();
      copy.setHost(getHost());
      copy.setPort(getPort());
      copy.setAnonymous(isAnonymous());
      copy.setUserName(getUserName());
      copy.setPassword(getPassword());
      copy.setPrivateKeyFile(getPrivateKeyFile());
      copy.setKnownHostsFile(getKnownHostsFile());
      copy.setPassphrase(getPassphrase());
      copy.setUseKeyPair(isUseKeyPair());

      copy.setInterpreterPath(getInterpreterPath());
      return copy;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public String getHost() {
    return myHost;
  }

  public void setHost(String host) {
    myHost = host;
  }

  public int getPort() {
    return myPort;
  }

  public void setPort(int port) {
    myPort = port;
  }

  @Transient
  public String getUserName() {
    return myUserName;
  }

  public void setUserName(String userName) {
    myUserName = userName;
  }


  public String getSerializedUserName() {
    if (myAnonymous) return "";
    return myUserName;
  }

  public void setSerializedUserName(String userName) {
    if (StringUtil.isEmpty(userName)) {
      myUserName = null;
    }
    else {
      myUserName = userName;
    }
  }

  public String getPassword() {
    return myPassword;
  }

  public void setPassword(String password) {
    myPassword = password;
  }

  public String getSerializedPassword() {
    if (myAnonymous) return "";

    if (myStorePassword) {
      return PasswordUtil.encodePassword(myPassword);
    }
    else {
      return "";
    }
  }

  public void setSerializedPassword(String serializedPassword) {
    if (!StringUtil.isEmpty(serializedPassword)) {
      myPassword = PasswordUtil.decodePassword(serializedPassword);
      myStorePassword = true;
    }
    else {
      myPassword = null;
    }
  }

  public void setStorePassword(boolean storePassword) {
    myStorePassword = true;
  }

  public void setStorePassphrase(boolean storePassphrase) {
    myStorePassphrase = storePassphrase;
  }

  public boolean isAnonymous() {
    return myAnonymous;
  }

  public void setAnonymous(boolean anonymous) {
    myAnonymous = anonymous;
  }
  
  public String getPrivateKeyFile() {
    return myPrivateKeyFile;
  }

  public void setPrivateKeyFile(String privateKeyFile) {
    myPrivateKeyFile = privateKeyFile;
  }
  

  public String getKnownHostsFile() {
    return myKnownHostsFile;
  }

  public void setKnownHostsFile(String knownHostsFile) {
    myKnownHostsFile = knownHostsFile;
  }

  @Transient
  public String getPassphrase() {
    return myPassphrase;
  }

  public void setPassphrase(String passphrase) {
    myPassphrase = passphrase;
  }

  @Nullable
  public String getSerializedPassphrase() {
    if (myStorePassphrase) {
      return PasswordUtil.encodePassword(myPassphrase);
    }
    else {
      return "";
    }
  }

  public void setSerializedPassphrase(String serializedPassphrase) {
    if (!StringUtil.isEmpty(serializedPassphrase)) {
      myPassphrase = PasswordUtil.decodePassword(serializedPassphrase);
      myStorePassphrase = true;
    }
    else {
      myPassphrase = null;
      myStorePassphrase = false;
    }
  }

  public boolean isUseKeyPair() {
    return myUseKeyPair;
  }

  public void setUseKeyPair(boolean useKeyPair) {
    myUseKeyPair = useKeyPair;
  }

  public static boolean isRemoteSdk(@Nullable String path) {
    if (path != null) {
      return path.startsWith(SSH_PREFIX);
    }
    else {
      return false;
    }
  }

  @NotNull
  public static PythonRemoteSdkAdditionalData loadRemote(Sdk sdk, @Nullable Element element) {
    final PythonRemoteSdkAdditionalData data = new PythonRemoteSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
    load(element, data);

    if (element != null) {
      data.setHost(element.getAttributeValue(HOST));
      data.setPort(Integer.parseInt(element.getAttributeValue(PORT)));
      data.setAnonymous(Boolean.parseBoolean(element.getAttributeValue(ANONYMOUS)));
      data.setSerializedUserName(element.getAttributeValue(USERNAME));
      data.setSerializedPassword(element.getAttributeValue(PASSWORD));
      data.setPrivateKeyFile(StringUtil.nullize(element.getAttributeValue(PRIVATE_KEY_FILE)));
      data.setKnownHostsFile(StringUtil.nullize(element.getAttributeValue(KNOWN_HOSTS_FILE)));
      data.setSerializedPassphrase(element.getAttributeValue(PASSPHRASE));
      data.setUseKeyPair(Boolean.parseBoolean(element.getAttributeValue(USE_KEY_PAIR)));
    }

    return data;
  }
}

