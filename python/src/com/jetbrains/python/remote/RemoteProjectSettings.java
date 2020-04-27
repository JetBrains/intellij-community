// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

import com.jetbrains.python.newProject.PyNewProjectSettings;

public class RemoteProjectSettings extends PyNewProjectSettings {
  private final String myDeploymentName;
  private final String myRemoteRoot;


  public RemoteProjectSettings(String deploymentName, String remoteRoot) {
    myDeploymentName = deploymentName;
    myRemoteRoot = remoteRoot;
  }

  public String getDeploymentName() {
    return myDeploymentName;
  }

  public String getRemoteRoot() {
    return myRemoteRoot;
  }
}
