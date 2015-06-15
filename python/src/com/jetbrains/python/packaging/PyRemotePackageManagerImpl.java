/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteFile;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.RemoteSdkCredentials;
import com.intellij.remote.VagrantNotStartedException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyRemotePackageManagerImpl extends PyPackageManagerImpl {
  private static final Logger LOG = Logger.getInstance(PyRemotePackageManagerImpl.class);

  PyRemotePackageManagerImpl(@NotNull Sdk sdk) {
    super(sdk);
  }

  @Nullable
  @Override
  protected String getHelperPath(String helper) throws ExecutionException {
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    if (sdkData instanceof PyRemoteSdkAdditionalDataBase) {
      final PyRemoteSdkAdditionalDataBase remoteSdkData = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
      try {
        final RemoteSdkCredentials remoteSdkCredentials = remoteSdkData.getRemoteSdkCredentials(false);
        if (!StringUtil.isEmpty(remoteSdkCredentials.getHelpersPath())) {
          return new RemoteFile(remoteSdkCredentials.getHelpersPath(), helper).getPath();
        }
        else {
          return null;
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (ExecutionException e) {
        throw analyzeException(e, helper, Collections.<String>emptyList());
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected ProcessOutput getPythonProcessOutput(@NotNull String helperPath,
                                                 @NotNull List<String> args,
                                                 boolean askForSudo,
                                                 boolean showProgress, @Nullable String workingDir) throws ExecutionException {
    final String homePath = mySdk.getHomePath();
    if (homePath == null) {
      throw new ExecutionException("Cannot find Python interpreter for SDK " + mySdk.getName());
    }
    final SdkAdditionalData sdkData = mySdk.getSdkAdditionalData();
    if (sdkData instanceof PyRemoteSdkAdditionalDataBase) { //remote interpreter
      RemoteSdkCredentials remoteSdkCredentials;
      try {
        remoteSdkCredentials = ((RemoteSdkAdditionalData)sdkData).getRemoteSdkCredentials(false);
      }
      catch (InterruptedException e) {
        LOG.error(e);
        remoteSdkCredentials = null;
      }
      catch (ExecutionException e) {
        throw analyzeException(e, helperPath, args);
      }
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null && remoteSdkCredentials != null) {
        final List<String> cmdline = new ArrayList<String>();
        cmdline.add(homePath);
        cmdline.add(RemoteFile.detectSystemByPath(homePath).createRemoteFile(helperPath).getPath());
        cmdline.addAll(Collections2.transform(args, new Function<String, String>() {
          @Override
          public String apply(@Nullable String input) {
            return quoteIfNeeded(input);
          }
        }));
        if (askForSudo) {
          askForSudo = !manager.ensureCanWrite(null, remoteSdkCredentials, remoteSdkCredentials.getInterpreterPath());
        }
        ProcessOutput processOutput;
        do {
          PathMappingSettings mappings = manager.setupMappings(null, (PyRemoteSdkAdditionalDataBase)sdkData, null);
          processOutput =
            manager.runRemoteProcess(null, remoteSdkCredentials, mappings, ArrayUtil.toStringArray(cmdline), workingDir, askForSudo);
          if (askForSudo && processOutput.getStderr().contains("sudo: 3 incorrect password attempts")) {
            continue;
          }
          break;
        }
        while (true);
        return processOutput;
      }
      else {
        throw new PyExecutionException(PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED, helperPath, args);
      }
    }
    else {
      throw new PyExecutionException("Invalid remote SDK", helperPath, args);
    }
  }

  private ExecutionException analyzeException(ExecutionException exception, String command, List<String> args) {
    final Throwable cause = exception.getCause();
    if (cause instanceof VagrantNotStartedException) {
      return new PyExecutionException("Vagrant instance is down", command, args, "", "", 0,
                                      ImmutableList.of(new LaunchVagrantFix(((VagrantNotStartedException)cause).getVagrantFolder(),
                                                                            ((VagrantNotStartedException)cause).getMachineName())));
    }
    return exception;
  }

  @Override
  protected void subscribeToLocalChanges(Sdk sdk) {
    // Local VFS changes aren't needed
  }

  @Override
  protected void installManagement(@NotNull String name) throws ExecutionException {
    super.installManagement(name);
    // TODO: remove temp directory for remote interpreter
  }

  private static String quoteIfNeeded(String arg) {
    return arg.replace("<", "\\<").replace(">", "\\>"); //TODO: move this logic to ParametersListUtil.encode
  }

  private class LaunchVagrantFix implements PyExecutionFix {
    @NotNull private final String myVagrantFolder;
    @Nullable private final String myMachineName;

    public LaunchVagrantFix(@NotNull String vagrantFolder, @Nullable String machineName) {
      myVagrantFolder = vagrantFolder;
      myMachineName = machineName;
    }

    @NotNull
    @Override
    public String getName() {
      return "Launch Vagrant";
    }

    @Override
    public void run(@NotNull Sdk sdk) {
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        try {
          manager.runVagrant(myVagrantFolder, myMachineName);
          PythonSdkType.getInstance().setupSdkPaths(mySdk);
          clearCaches();
        }
        catch (ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
