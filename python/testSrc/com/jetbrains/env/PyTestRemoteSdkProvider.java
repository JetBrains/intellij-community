package com.jetbrains.env;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSdkException;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyTestTask;
import com.jetbrains.python.remote.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
public class PyTestRemoteSdkProvider {
  public static final String SSH_PASSWORD = "SSH_PASSWORD";
  public static final String LOCAL_SSH_WITHOUT_PASSWORD = "LOCAL_SSH_WITHOUT_PASSWORD";
  private final PyRemoteInterpreterManagerImpl myInterpreterManager;
  private static final String SSH_USER_NAME = "SSH_USER_NAME";

  private Project myProject;

  private static PyTestRemoteSdkProvider INSTANCE = null;

  public synchronized static PyTestRemoteSdkProvider getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new PyTestRemoteSdkProvider();
    }
    return INSTANCE;
  }

  public PyTestRemoteSdkProvider() {
    myInterpreterManager = new PyRemoteInterpreterManagerImpl();
  }

  private final LoadingCache<String, Sdk> mySdkTable = CacheBuilder.newBuilder()
    .build(
      new CacheLoader<String, Sdk>() {
        @Override
        public Sdk load(String key) throws Exception {
          return createSdk(key);
        }
      }
    );

  public Sdk getSdk(Project project, String sdkPath) throws ExecutionException {
    myProject = project;
    return mySdkTable.get(sdkPath);
  }

  private Sdk createSdk(String interpreterPath) throws RemoteSdkException {
    try {
      PyRemoteSdkAdditionalData data = createRemoteSdkData(interpreterPath);

      final Sdk sdk = myInterpreterManager.createRemoteSdk(myProject, data, null, Lists.<Sdk>newArrayList());
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          myInterpreterManager.initSdk(sdk, myProject, null);
        }
      });

      return sdk;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static PyRemoteSdkCredentialsHolder getTestSdkCredentials(String path) {
    PyRemoteSdkCredentialsHolder data = new PyRemoteSdkCredentialsHolder();
    data.setHost("localhost");
    data.setPort(22);
    data.setUserName(getUserName());
    data.setPassword(getPassword());

    data.setInterpreterPath(path);
    try {
      data.setHelpersPath(getTempHelpersPath(data));
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return data;
  }

  private static PyRemoteSdkAdditionalData createRemoteSdkData(final String path) throws IOException {
    PyRemoteSdkAdditionalData res = new PyRemoteSdkAdditionalData(path);
    res.setSshCredentials(getTestSdkCredentials(path));
    return res;

  }

  public static boolean canRunRemoteSdk() {
    return isPasswordSpecified();
  }

  private static boolean isPasswordSpecified() {
    return !StringUtil.isEmpty(System.getenv(LOCAL_SSH_WITHOUT_PASSWORD)) || !StringUtil.isEmpty(getPassword());
  }

  public static boolean shouldFailWhenCantRunRemote() {
    return !StringUtil.isEmpty(System.getenv("FAIL_WHEN_CANT_RUN_REMOTE")) || PyEnvTestCase.IS_UNDER_TEAMCITY;
  }

  private static String getTempHelpersPath(PyRemoteSdkCredentials data) throws IOException {
    final File dir = new File("/tmp");

    File tmpDir = FileUtil.createTempDirectory(dir, "pycharm_helpers_", "_" + Math.abs(
      Hashing.md5().hashString(data.getInterpreterPath()).asInt()), true);
    return tmpDir.getPath();
  }

  public static String getUserName() {
    String userName = System.getenv(SSH_USER_NAME);
    return StringUtil.isEmpty(userName) ? System.getProperty("user.name") : userName;
  }

  public static String getPassword() {
    return System.getenv(SSH_PASSWORD);
  }

  public static boolean shouldRunRemoteSdk(PyTestTask task) {
    return SystemInfo.isUnix && task instanceof RemoteSdkTestable;
  }
}
