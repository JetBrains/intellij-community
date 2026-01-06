// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.google.common.net.HostAndPort;
import com.intellij.openapi.util.Key;

/**
 * @deprecated use targets API instead
 */
@Deprecated
public final class PyRemoteProcessStarter {
  public static final Key<Boolean> OPEN_FOR_INCOMING_CONNECTION = Key.create("OPEN_FOR_INCOMING_CONNECTION");
  public static final Key<HostAndPort> WEB_SERVER_HOST_AND_PORT = new Key<>("WEB_SERVER_HOST_AND_PORT");
  /**
   * This key is used to give the hint for the process starter that the
   * process is auxiliary.
   * <p>
   * As for now this flag takes effect for Docker Compose process starters
   * which uses {@code docker-compose run} command to the contrary of the usual
   * process execution using {@code docker-compose up} command.
   */
  public static final Key<Boolean> RUN_AS_AUXILIARY_PROCESS = Key.create("RUN_AS_AUXILIARY_PROCESS");

  private PyRemoteProcessStarter() {

  }
}
