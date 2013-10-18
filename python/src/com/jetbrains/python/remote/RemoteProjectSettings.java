package com.jetbrains.python.remote;

/**
* @author traff
*/
public class RemoteProjectSettings {
  private String myDeploymentName;
  private String myRemoteRoot;


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
