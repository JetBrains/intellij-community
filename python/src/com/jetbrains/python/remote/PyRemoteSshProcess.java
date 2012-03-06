package com.jetbrains.python.remote;

import com.intellij.openapi.util.Pair;

/**
 * @author traff
 */
abstract public class PyRemoteSshProcess extends Process {
  public abstract void addRemoteTunnel(int remotePort, String host, int localPort) throws PyRemoteInterpreterException;

  public abstract void addLocalTunnel(int localPort, String host, int remotePort) throws PyRemoteInterpreterException;

  public abstract Pair<String, Integer> obtainRemoteSocket(String interpreterPath) throws PyRemoteInterpreterException;

  public abstract PyRemoteSshProcess exec(String command) throws PyRemoteInterpreterException;
}
