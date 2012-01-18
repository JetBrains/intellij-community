package com.jetbrains.python.remote;

/**
 * @author traff
 */
public interface PyRemoteInterpreterSettings {
  String getHost();

  int getPort();

  String getInterpreterPath();

  String getUserName();

  String getPassword();

  boolean isAnonymous();

  String getPrivateKeyFile();

  String getKnownHostsFile();

  String getPassphrase();

  boolean isUseKeyPair();
}
