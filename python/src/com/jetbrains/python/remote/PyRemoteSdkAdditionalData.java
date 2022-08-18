package com.jetbrains.python.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.*;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.util.Consumer;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.WinPythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PyRemoteSdkAdditionalData extends PythonSdkAdditionalData implements PyRemoteSdkAdditionalDataBase {
  private static final String PYCHARM_HELPERS = ".pycharm_helpers";
  private static final String SKELETONS_PATH = "SKELETONS_PATH";
  private static final String VERSION = "VERSION";
  private static final String RUN_AS_ROOT_VIA_SUDO = "RUN_AS_ROOT_VIA_SUDO";

  private final RemoteConnectionCredentialsWrapper myRemoteConnectionCredentialsWrapper = new RemoteConnectionCredentialsWrapper();

  private final RemoteSdkPropertiesHolder myRemoteSdkProperties = new RemoteSdkPropertiesHolder(PYCHARM_HELPERS);

  private String mySkeletonsPath;

  private String myVersionString;

  public PyRemoteSdkAdditionalData(String interpreterPath) {
    this(interpreterPath, false);
  }

  public PyRemoteSdkAdditionalData(String interpreterPath, boolean runAsRootViaSudo) {
    super(computeFlavor(interpreterPath));
    setInterpreterPath(interpreterPath);
    setRunAsRootViaSudo(runAsRootViaSudo);
  }

  private RemoteSdkCredentialsProducer<PyRemoteSdkCredentials> getProducer() {
    PythonSshInterpreterManager manager = PythonSshInterpreterManager.Factory.getInstance();
    if (manager != null) {
      return manager.getRemoteSdkCredentialsProducer(credentials -> createPyRemoteSdkCredentials(credentials), myRemoteConnectionCredentialsWrapper);
    }
    else {
      throw new IllegalStateException("No plugin");
      //TODO:
    }
  }

  @Override
  public @NotNull RemoteConnectionCredentialsWrapper connectionCredentials() {
    return myRemoteConnectionCredentialsWrapper;
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
    List<PythonSdkFlavor> result = new ArrayList<>();
    if (isWindows) {
      result.add(WinPythonSdkFlavor.getInstance());
    }
    else {
      result.add(UnixPythonSdkFlavor.getInstance());
    }
    result.addAll(PythonSdkFlavor.getPlatformIndependentFlavors());

    return result;
  }

  public String getSkeletonsPath() {
    return mySkeletonsPath;
  }

  public void setSkeletonsPath(String path) {
    mySkeletonsPath = path;
  }

  @Override
  public <C> void setCredentials(Key<C> key, C credentials) {
    myRemoteConnectionCredentialsWrapper.setCredentials(key, credentials);
  }

  @Override
  public CredentialsType getRemoteConnectionType() {
    return myRemoteConnectionCredentialsWrapper.getRemoteConnectionType();
  }

  @Override
  public void switchOnConnectionType(CredentialsCase... cases) {
    myRemoteConnectionCredentialsWrapper.switchType(cases);
  }

  private PyRemoteSdkCredentials createPyRemoteSdkCredentials(@NotNull RemoteCredentials credentials) {
    PyRemoteSdkCredentialsHolder res = new PyRemoteSdkCredentialsHolder();
    RemoteSdkCredentialsBuilder.copyCredentials(credentials, res);
    myRemoteSdkProperties.copyTo(res);
    res.setSkeletonsPath(getSkeletonsPath());
    res.setInitialized(isInitialized());
    res.setValid(isValid());
    res.setSdkId(getSdkId());
    return res;
  }

  @Override
  public String getInterpreterPath() {
    return myRemoteSdkProperties.getInterpreterPath();
  }

  @Override
  public void setInterpreterPath(String interpreterPath) {
    myRemoteSdkProperties.setInterpreterPath(interpreterPath);
  }

  @Override
  public boolean isRunAsRootViaSudo() {
    return myRemoteSdkProperties.isRunAsRootViaSudo();
  }

  @Override
  public void setRunAsRootViaSudo(boolean runAsRootViaSudo) {
    myRemoteSdkProperties.setRunAsRootViaSudo(runAsRootViaSudo);
  }

  @Override
  public String getHelpersPath() {
    return myRemoteSdkProperties.getHelpersPath();
  }

  @Override
  public void setHelpersPath(String helpersPath) {
    myRemoteSdkProperties.setHelpersPath(helpersPath);
  }

  @Override
  public String getDefaultHelpersName() {
    return myRemoteSdkProperties.getDefaultHelpersName();
  }

  @NotNull
  @Override
  public PathMappingSettings getPathMappings() {
    return myRemoteSdkProperties.getPathMappings();
  }

  @Override
  public void setPathMappings(@Nullable PathMappingSettings pathMappings) {
    myRemoteSdkProperties.setPathMappings(pathMappings);
  }

  @Override
  public boolean isHelpersVersionChecked() {
    return myRemoteSdkProperties.isHelpersVersionChecked();
  }

  @Override
  public void setHelpersVersionChecked(boolean helpersVersionChecked) {
    myRemoteSdkProperties.setHelpersVersionChecked(helpersVersionChecked);
  }

  @Override
  public void setSdkId(String sdkId) {
    throw new IllegalStateException("sdkId in this class is constructed based on fields, so it can't be set");
  }

  @Override
  public String getSdkId() {
    return constructSdkID(myRemoteConnectionCredentialsWrapper, myRemoteSdkProperties);
  }

  public String getPresentableDetails() {
    return myRemoteConnectionCredentialsWrapper.getPresentableDetails(myRemoteSdkProperties.getInterpreterPath());
  }

  private static String constructSdkID(@NotNull RemoteConnectionCredentialsWrapper remoteConnectionCredentialsWrapper,
                                       @NotNull RemoteSdkPropertiesHolder properties) {
    return remoteConnectionCredentialsWrapper.getId() + properties.getInterpreterPath();
  }

  @Override
  public boolean isInitialized() {
    return myRemoteSdkProperties.isInitialized();
  }

  @Override
  public void setInitialized(boolean initialized) {
    myRemoteSdkProperties.setInitialized(initialized);
  }

  @Override
  public boolean isValid() {
    return myRemoteSdkProperties.isValid();
  }

  @Override
  public void setValid(boolean valid) {
    myRemoteSdkProperties.setValid(valid);
  }

  @Override
  @Deprecated
  public PyRemoteSdkCredentials getRemoteSdkCredentials() throws InterruptedException, ExecutionException {
    return getProducer().getRemoteSdkCredentials();
  }

  @Override
  public PyRemoteSdkCredentials getRemoteSdkCredentials(boolean allowSynchronousInteraction)
    throws InterruptedException, ExecutionException {
    return getProducer().getRemoteSdkCredentials(allowSynchronousInteraction);
  }

  @Override
  public PyRemoteSdkCredentials getRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction)
    throws InterruptedException,
           ExecutionException {
    return getProducer().getRemoteSdkCredentials(allowSynchronousInteraction);
  }

  public boolean connectionEquals(PyRemoteSdkAdditionalData data) {
    return myRemoteConnectionCredentialsWrapper.equals(data.myRemoteConnectionCredentialsWrapper);
  }

  @Override
  public Object getRemoteSdkDataKey() {
    return myRemoteConnectionCredentialsWrapper.getConnectionKey();
  }

  @Override
  public void produceRemoteSdkCredentials(final Consumer<? super PyRemoteSdkCredentials> remoteSdkCredentialsConsumer) {
    getProducer().produceRemoteSdkCredentials(remoteSdkCredentialsConsumer);
  }

  @Override
  public void produceRemoteSdkCredentials(final boolean allowSynchronousInteraction,
                                          final Consumer<? super PyRemoteSdkCredentials> remoteSdkCredentialsConsumer) {
    getProducer().produceRemoteSdkCredentials(allowSynchronousInteraction, remoteSdkCredentialsConsumer);
  }

  @Override
  public void produceRemoteSdkCredentials(@Nullable Project project, final boolean allowSynchronousInteraction,
                                          final Consumer<? super PyRemoteSdkCredentials> remoteSdkCredentialsConsumer) {
    getProducer().produceRemoteSdkCredentials(allowSynchronousInteraction, remoteSdkCredentialsConsumer);
  }

  @NotNull
  @Override
  public PyRemoteSdkAdditionalData copy() {
    PyRemoteSdkAdditionalData copy = new PyRemoteSdkAdditionalData(myRemoteSdkProperties.getInterpreterPath(), isRunAsRootViaSudo());

    copyTo(copy);

    return copy;
  }

  public void copyTo(@NotNull PyRemoteSdkAdditionalData copy) {
    copy.setSkeletonsPath(mySkeletonsPath);
    copy.setVersionString(myVersionString);
    myRemoteSdkProperties.copyTo(copy.myRemoteSdkProperties);
    myRemoteConnectionCredentialsWrapper.copyTo(copy.myRemoteConnectionCredentialsWrapper);
  }

  @Override
  public void save(@NotNull final Element rootElement) {
    super.save(rootElement);


    myRemoteSdkProperties.save(rootElement);

    rootElement.setAttribute(SKELETONS_PATH, StringUtil.notNullize(getSkeletonsPath()));
    rootElement.setAttribute(VERSION, StringUtil.notNullize(getVersionString()));
    rootElement.setAttribute(RUN_AS_ROOT_VIA_SUDO, Boolean.toString(isRunAsRootViaSudo()));

    // this should be executed at the end because of the case with UnknownCredentialsHolder
    myRemoteConnectionCredentialsWrapper.save(rootElement);
  }


  @NotNull
  public static PyRemoteSdkAdditionalData loadRemote(@NotNull Sdk sdk, @Nullable Element element) {
    final String path = sdk.getHomePath();
    assert path != null;
    final PyRemoteSdkAdditionalData data = new PyRemoteSdkAdditionalData(RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath(path), false);
    data.load(element);

    if (element != null) {
      WSLUtil.fixWslPrefix(sdk);
      CredentialsManager.getInstance().loadCredentials(path, element, data);
      if (data.myRemoteConnectionCredentialsWrapper.getRemoteConnectionType().hasPrefix(RemoteCredentialsHolder.SSH_PREFIX)) {
        CredentialsManager.updateOutdatedSdk(data, null);
      }
      data.myRemoteSdkProperties.load(element);

      data.setSkeletonsPath(StringUtil.nullize(element.getAttributeValue(SKELETONS_PATH)));
      String helpersPath = StringUtil.nullize(element.getAttributeValue("PYCHARM_HELPERS_PATH"));
      if (helpersPath != null) {
        data.setHelpersPath(helpersPath);
      }
      data.setVersionString(StringUtil.nullize(element.getAttributeValue(VERSION)));
      data.setRunAsRootViaSudo(StringUtil.equals(element.getAttributeValue(RUN_AS_ROOT_VIA_SUDO), "true"));
    }

    return data;
  }

  @Override
  public void setVersionString(String versionString) {
    myVersionString = versionString;
  }

  @Override
  public String getVersionString() {
    return myVersionString;
  }
}
