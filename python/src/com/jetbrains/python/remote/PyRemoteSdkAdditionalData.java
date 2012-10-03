package com.jetbrains.python.remote;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.plugins.remotesdk.RemoteSdkAdditionalData;
import com.jetbrains.plugins.remotesdk.RemoteSdkDataHolder;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import com.jetbrains.python.sdk.UnixPythonSdkFlavor;
import com.jetbrains.python.sdk.WinPythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class PyRemoteSdkAdditionalData extends PythonSdkAdditionalData implements RemoteSdkAdditionalData {
  final private RemoteSdkDataHolder myRemoteSdkDataHolder = new RemoteSdkDataHolder();

  private final static String SKELETONS_PATH = "SKELETONS_PATH";

  private String mySkeletonsPath;

  public PyRemoteSdkAdditionalData(@Nullable PythonSdkFlavor flavor) {
    super(flavor);
  }

  public PyRemoteSdkAdditionalData(@NotNull String interpreterPath) {
    super(computeFlavor(interpreterPath));
    setInterpreterPath(interpreterPath);
  }


  public void setSkeletonsPath(String path) {
    mySkeletonsPath = path;
  }

  public String getSkeletonsPath() {
    return mySkeletonsPath;
  }

  @Override
  public String getInterpreterPath() {
    return myRemoteSdkDataHolder.getInterpreterPath();
  }


  @Override
  public void setInterpreterPath(String interpreterPath) {
    myRemoteSdkDataHolder.setInterpreterPath(interpreterPath);
  }

  @Override
  public String getFullInterpreterPath() {
    return myRemoteSdkDataHolder.getFullInterpreterPath();
  }

  @Override
  public String getTempFilesPath() {
    return myRemoteSdkDataHolder.getTempFilesPath();
  }

  @Override
  public String getHelpersPath() {
    return myRemoteSdkDataHolder.getHelpersPath();
  }

  @Override
  public void setTempFilesPath(String tempFilesPath) {
    myRemoteSdkDataHolder.setTempFilesPath(tempFilesPath);
  }

  @Override
  public String getHost() {
    return myRemoteSdkDataHolder.getHost();
  }

  @Override
  public void setHost(String host) {
    myRemoteSdkDataHolder.setHost(host);
  }

  @Override
  public int getPort() {
    return myRemoteSdkDataHolder.getPort();
  }

  @Override
  public void setPort(int port) {
    myRemoteSdkDataHolder.setPort(port);
  }

  @Override
  public String getUserName() {
    return myRemoteSdkDataHolder.getUserName();
  }

  @Override
  public void setUserName(String userName) {
    myRemoteSdkDataHolder.setUserName(userName);
  }

  @Override
  public String getPassword() {
    return myRemoteSdkDataHolder.getPassword();
  }

  @Override
  public void setPassword(String password) {
    myRemoteSdkDataHolder.setPassword(password);
  }

  @Override
  public void setStorePassword(boolean storePassword) {
    myRemoteSdkDataHolder.setStorePassword(storePassword);
  }

  @Override
  public void setStorePassphrase(boolean storePassphrase) {
    myRemoteSdkDataHolder.setStorePassphrase(storePassphrase);
  }

  @Override
  public boolean isStorePassword() {
    return myRemoteSdkDataHolder.isStorePassword();
  }

  @Override
  public boolean isStorePassphrase() {
    return myRemoteSdkDataHolder.isStorePassphrase();
  }

  @Override
  public boolean isAnonymous() {
    return myRemoteSdkDataHolder.isAnonymous();
  }

  @Override
  public void setAnonymous(boolean anonymous) {
    myRemoteSdkDataHolder.setAnonymous(anonymous);
  }

  @Override
  public String getPrivateKeyFile() {
    return myRemoteSdkDataHolder.getPrivateKeyFile();
  }

  @Override
  public void setPrivateKeyFile(String privateKeyFile) {
    myRemoteSdkDataHolder.setPrivateKeyFile(privateKeyFile);
  }

  @Override
  public String getKnownHostsFile() {
    return myRemoteSdkDataHolder.getKnownHostsFile();
  }

  @Override
  public void setKnownHostsFile(String knownHostsFile) {
    myRemoteSdkDataHolder.setKnownHostsFile(knownHostsFile);
  }

  @Override
  public String getPassphrase() {
    return myRemoteSdkDataHolder.getPassphrase();
  }

  @Override
  public void setPassphrase(String passphrase) {
    myRemoteSdkDataHolder.setPassphrase(passphrase);
  }

  @Override
  public boolean isUseKeyPair() {
    return myRemoteSdkDataHolder.isUseKeyPair();
  }

  @Override
  public void setUseKeyPair(boolean useKeyPair) {
    myRemoteSdkDataHolder.setUseKeyPair(useKeyPair);
  }

  @Override
  public void addRemoteRoot(String remoteRoot) {
    myRemoteSdkDataHolder.addRemoteRoot(remoteRoot);
  }

  @Override
  public void clearRemoteRoots() {
    myRemoteSdkDataHolder.clearRemoteRoots();
  }

  @Override
  public List<String> getRemoteRoots() {
    return myRemoteSdkDataHolder.getRemoteRoots();
  }

  @Override
  public void setRemoteRoots(List<String> remoteRoots) {
    myRemoteSdkDataHolder.setRemoteRoots(remoteRoots);
  }

  @Override
  public boolean isHelpersVersionChecked() {
    return myRemoteSdkDataHolder.isHelpersVersionChecked();
  }

  @Override
  public void setHelpersVersionChecked(boolean helpersVersionChecked) {
    myRemoteSdkDataHolder.setHelpersVersionChecked(helpersVersionChecked);
  }

  @NotNull
  public static PyRemoteSdkAdditionalData loadRemote(Sdk sdk, @Nullable Element element) {
    final PyRemoteSdkAdditionalData data = new PyRemoteSdkAdditionalData(sdk.getHomePath());
    load(element, data);

    if (element != null) {
      data.myRemoteSdkDataHolder.loadRemoteSdkData(element);
      data.setSkeletonsPath(StringUtil.nullize(element.getAttributeValue(SKELETONS_PATH)));
      String helpers_path = StringUtil.nullize(element.getAttributeValue("PYCHARM_HELPERS_PATH"));
      if (helpers_path != null) {
        data.setTempFilesPath(helpers_path);
      }
    }

    return data;
  }


  @Nullable
  private static PythonSdkFlavor computeFlavor(@Nullable String sdkPath) {
    if (sdkPath == null) {
      return null;
    }
    for (PythonSdkFlavor flavor : getApplicableFlavors(sdkPath.contains("\\"))) {
      if (flavor.isValidSdkPath(new File(sdkPath))) {
        return flavor;
      }
    }
    return null;
  }

  private static List<PythonSdkFlavor> getApplicableFlavors(boolean isWindows) {
    List<PythonSdkFlavor> result = new ArrayList<PythonSdkFlavor>();
    if (isWindows) {
      result.add(WinPythonSdkFlavor.INSTANCE);
    }
    else {
      result.add(UnixPythonSdkFlavor.INSTANCE);
    }
    result.addAll(PythonSdkFlavor.getPlatformIndependentFlavors());

    return result;
  }

  @Override
  public void save(@NotNull final Element rootElement) {
    super.save(rootElement);

    myRemoteSdkDataHolder.saveRemoteSdkData(rootElement);

    rootElement.setAttribute(SKELETONS_PATH, StringUtil.notNullize(getSkeletonsPath()));
  }

  @Nullable
  @Override
  public Object clone() throws CloneNotSupportedException {
    try {
      final PyRemoteSdkAdditionalData copy = (PyRemoteSdkAdditionalData)super.clone();
      copyTo(copy);

      return copy;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public void copyTo(PyRemoteSdkAdditionalData copy) {
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
    copy.setStorePassword(isStorePassword());
    copy.setStorePassphrase(isStorePassphrase());
    copy.setHelpersVersionChecked(isHelpersVersionChecked());

    copy.setSkeletonsPath(getSkeletonsPath());
    copy.setRemoteRoots(getRemoteRoots());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyRemoteSdkAdditionalData data = (PyRemoteSdkAdditionalData)o;

    if (!myRemoteSdkDataHolder.equals(data.myRemoteSdkDataHolder)) return false;
    if (!mySkeletonsPath.equals(data.mySkeletonsPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRemoteSdkDataHolder.hashCode();
    result = 31 * result + mySkeletonsPath.hashCode();
    return result;
  }
}

