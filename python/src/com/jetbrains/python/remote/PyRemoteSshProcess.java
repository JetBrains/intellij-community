package com.jetbrains.python.remote;

/**
 * @author traff
 */
abstract public class PyRemoteSshProcess extends Process {
  public abstract void addTunnelRemote(int remotePort, String host, int localPort) throws PyRemoteInterpreterException;

  public abstract void addTunnelLocal(int localPort, String host, int remotePort) throws PyRemoteInterpreterException;
}
