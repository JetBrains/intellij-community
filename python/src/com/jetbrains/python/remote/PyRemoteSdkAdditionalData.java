// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.*;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.WinPythonSdkFlavor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PyRemoteSdkAdditionalData extends PythonSdkAdditionalData implements PyRemoteSdkAdditionalDataBase {
  public static final String PYCHARM_HELPERS = ".pycharm_helpers";

  private static final String SKELETONS_PATH = "SKELETONS_PATH";
  private static final String VERSION = "VERSION";
  private static final String RUN_AS_ROOT_VIA_SUDO = "RUN_AS_ROOT_VIA_SUDO";

  private final RemoteConnectionCredentialsWrapper myWrapper = new RemoteConnectionCredentialsWrapper();
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

  @Override
  public @NotNull RemoteConnectionCredentialsWrapper connectionCredentials() {
    return myWrapper;
  }

  private static @Nullable PythonSdkFlavor<?> computeFlavor(@Nullable String sdkPath) {
    if (sdkPath != null) {
      // FIXME: converge with sdk flavor & use os.isWindows
      for (var flavor : getApplicableFlavors(sdkPath.contains("\\"))) {
        if (flavor.isValidSdkPath(sdkPath)) {
          return flavor;
        }
      }
    }
    return null;
  }

  private static List<PythonSdkFlavor<?>> getApplicableFlavors(boolean isWindows) {
    var result = new ArrayList<PythonSdkFlavor<?>>();
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
    myWrapper.setCredentials(key, credentials);
  }

  @Override
  public CredentialsType<?> getRemoteConnectionType() {
    return myWrapper.getRemoteConnectionType();
  }

  @Override
  public void switchOnConnectionType(CredentialsCase... cases) {
    myWrapper.switchType(cases);
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

  @Override
  public @NotNull PathMappingSettings getPathMappings() {
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
    return constructSdkID(myWrapper, myRemoteSdkProperties);
  }

  public String getPresentableDetails() {
    return myWrapper.getPresentableDetails(myRemoteSdkProperties.getInterpreterPath());
  }

  private static String constructSdkID(RemoteConnectionCredentialsWrapper wrapper, RemoteSdkPropertiesHolder properties) {
    return wrapper.getId() + properties.getInterpreterPath();
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
  public RemoteCredentials getRemoteCredentials(@Nullable Project project, boolean allowSynchronousInteraction) throws InterruptedException, ExecutionException {
    var manager = PythonSshInterpreterManager.Factory.getInstance();
    if (manager == null) throw new IllegalStateException("No plugin");
    return manager.getRemoteCredentials(myWrapper, project, allowSynchronousInteraction);
  }

  @Override
  public void produceRemoteCredentials(@Nullable Project project, boolean allowSynchronousInteraction, @NotNull Consumer<RemoteCredentials> consumer) {
    var manager = PythonSshInterpreterManager.Factory.getInstance();
    if (manager == null) throw new IllegalStateException("No plugin");
    manager.produceRemoteCredentials(myWrapper, project, allowSynchronousInteraction, consumer);
  }

  public boolean connectionEquals(PyRemoteSdkAdditionalData data) {
    return myWrapper.equals(data.myWrapper);
  }

  @Override
  public Object getRemoteSdkDataKey() {
    return myWrapper.getConnectionKey();
  }

  @Override
  public @NotNull PyRemoteSdkAdditionalData copy() {
    var copy = new PyRemoteSdkAdditionalData(myRemoteSdkProperties.getInterpreterPath(), isRunAsRootViaSudo());
    copyTo(copy);
    return copy;
  }

  public void copyTo(@NotNull PyRemoteSdkAdditionalData copy) {
    copy.setSkeletonsPath(mySkeletonsPath);
    copy.setVersionString(myVersionString);
    myRemoteSdkProperties.copyTo(copy.myRemoteSdkProperties);
    myWrapper.copyTo(copy.myWrapper);
  }

  @Override
  public void save(final @NotNull Element rootElement) {
    super.save(rootElement);
    myRemoteSdkProperties.save(rootElement);
    rootElement.setAttribute(SKELETONS_PATH, StringUtil.notNullize(getSkeletonsPath()));
    rootElement.setAttribute(VERSION, StringUtil.notNullize(getVersionString()));
    rootElement.setAttribute(RUN_AS_ROOT_VIA_SUDO, Boolean.toString(isRunAsRootViaSudo()));
    // this should be executed at the end because of the case with UnknownCredentialsHolder
    myWrapper.save(rootElement);
  }

  public static @NotNull PyRemoteSdkAdditionalData loadRemote(@NotNull Sdk sdk, @Nullable Element element) {
    var path = sdk.getHomePath();
    assert path != null;
    var data = new PyRemoteSdkAdditionalData(RemoteSdkProperties.getInterpreterPathFromFullPath(path), false);
    data.load(element);

    if (element != null) {
      CredentialsManager.getInstance().loadCredentials(path, element, data);
      if (data.myWrapper.getRemoteConnectionType().hasPrefix(RemoteCredentialsHolder.SSH_PREFIX)) {
        CredentialsManager.updateOutdatedSdk(data, null);
      }
      data.myRemoteSdkProperties.load(element);

      data.setSkeletonsPath(StringUtil.nullize(element.getAttributeValue(SKELETONS_PATH)));
      var helpersPath = StringUtil.nullize(element.getAttributeValue("PYCHARM_HELPERS_PATH"));
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
