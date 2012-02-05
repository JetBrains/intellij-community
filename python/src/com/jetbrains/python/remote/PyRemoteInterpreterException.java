package com.jetbrains.python.remote;

import java.net.NoRouteToHostException;

/**
 * @author traff
 */
public class PyRemoteInterpreterException extends Exception {
  private final boolean myNoRouteToHost;
  private final boolean myAuthFailed;

  public PyRemoteInterpreterException(String s, Throwable throwable) {
    super(s, throwable);
    myNoRouteToHost = throwable instanceof NoRouteToHostException;
    myAuthFailed = false;
  }

  public PyRemoteInterpreterException(String s) {
    super(s);
    myAuthFailed = false;
    myNoRouteToHost = false;
  }

  public boolean isNoRouteToHost() {
    return myNoRouteToHost;
  }

  public boolean isAuthFailed() {
    return myAuthFailed;
  }

  public String getMessage() {
    if (myNoRouteToHost) {
      return getCause().getMessage();
    }
    else if (myAuthFailed) {
      return "Authentication failed";
    }
    else {
      return super.getMessage();
    }
  }
}
