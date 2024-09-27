package org.jetbrains.idea.maven.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NativeMavenProjectHolder extends Remote {
  public static NativeMavenProjectHolder NULL = new NativeMavenProjectHolder() {
    @Override
    public int getId() throws RemoteException {
      return 0;
    }
  };
  int getId() throws RemoteException;
}
