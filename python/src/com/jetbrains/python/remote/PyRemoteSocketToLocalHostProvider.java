package com.jetbrains.python.remote;

import com.intellij.openapi.util.Pair;
import com.intellij.remote.RemoteSdkException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Koshevoy
 */
public interface PyRemoteSocketToLocalHostProvider {
  /**
   * Returns {@code &lt;host, port&gt;} tuple with which socket on the remote host should be created to be connected to {@code localPort}
   * on local host.
   *
   * @param localPort port on the local host to which the remote host needs to establish connection
   * @return {@code &lt;host, port&gt;} with which socket on the remote host should be created
   * @throws RemoteSdkException
   */
  @NotNull
  Pair<String, Integer> getRemoteSocket(int localPort) throws RemoteSdkException;
}
