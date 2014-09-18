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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyRemotePackageManagerImpl extends PyPackageManagerImpl {
  private static final String LAUNCH_VAGRANT = "launchVagrant";
  public static final int ERROR_VAGRANT_NOT_LAUNCHED = 101;
  public static final int ERROR_REMOTE_ACCESS = 102;

  private static final Logger LOG = Logger.getInstance(PyRemotePackageManagerImpl.class);

  PyRemotePackageManagerImpl(@NotNull Sdk sdk) {
    super(sdk);
  }

  @Nullable
  @Override
  protected String getHelperPath(String helper) {
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
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @Override
  protected ProcessOutput getProcessOutput(@NotNull String helperPath,
                                           @NotNull List<String> args,
                                           boolean askForSudo,
                                           @Nullable String workingDir) throws PyExternalProcessException {
    final String homePath = mySdk.getHomePath();
    if (homePath == null) {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args, "Cannot find interpreter for SDK");
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
      catch (final ExecutionException e) {
        if (e.getCause() instanceof VagrantNotStartedException) {
          throw new PyExternalProcessException(ERROR_VAGRANT_NOT_LAUNCHED, helperPath, args, "Vagrant instance is down. <a href=\"" +
                                                                                             LAUNCH_VAGRANT +
                                                                                             "\">Launch vagrant</a>")
            .withHandler(LAUNCH_VAGRANT, new Runnable() {
              @Override
              public void run() {
                final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
                if (manager != null) {

                  try {
                    manager.runVagrant(((VagrantNotStartedException)e.getCause()).getVagrantFolder());
                    clearCaches();
                  }
                  catch (ExecutionException e1) {
                    throw new RuntimeException(e1);
                  }
                }
              }
            });
        }
        else {
          throw new PyExternalProcessException(ERROR_REMOTE_ACCESS, helperPath, args, e.getMessage());
        }
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
        try {
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
        catch (ExecutionException e) {
          throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args, "Error running SDK: " + e.getMessage(), e);
        }
      }
      else {
        throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args,
                                             PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
      }
    }
    else {
      throw new PyExternalProcessException(ERROR_INVALID_SDK, helperPath, args, "Invalid remote SDK");
    }
  }

  @Override
  protected void subscribeToLocalChanges(Sdk sdk) {
    // Local VFS changes aren't needed
  }

  @Override
  protected void installManagement(@NotNull String name) throws PyExternalProcessException {
    super.installManagement(name);
    // TODO: remove temp directory for remote interpreter
  }

  private static String quoteIfNeeded(String arg) {
    return arg.replace("<", "\\<").replace(">", "\\>"); //TODO: move this logic to ParametersListUtil.encode
  }
}
