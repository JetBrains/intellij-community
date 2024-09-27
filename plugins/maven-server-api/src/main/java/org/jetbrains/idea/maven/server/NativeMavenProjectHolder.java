package org.jetbrains.idea.maven.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @deprecated NativeMavenProjectHolder is not used anymore. Use MavenProject instead
 */
@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
public interface NativeMavenProjectHolder extends Remote {
  NativeMavenProjectHolder NULL = new NativeMavenProjectHolder() {
    @Override
    public int getId() throws RemoteException {
      return 0;
    }
  };
  int getId() throws RemoteException;
}
