package org.jetbrains.idea.maven.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NativeMavenProjectHolder extends Remote {
  int getId() throws RemoteException;
}
