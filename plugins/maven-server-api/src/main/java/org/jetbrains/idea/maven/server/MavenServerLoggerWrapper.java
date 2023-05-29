// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.util.ExceptionUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MavenServerLoggerWrapper extends MavenRemoteObject implements MavenPullServerLogger  {
  private final ConcurrentLinkedQueue<ServerLogEvent> myPullingQueue = new ConcurrentLinkedQueue<ServerLogEvent>();

  public void info(Throwable e) {
    myPullingQueue.add(new ServerLogEvent(ServerLogEvent.Type.INFO, serialize(e)));
  }

  public void warn(Throwable e) {
    myPullingQueue.add(new ServerLogEvent(ServerLogEvent.Type.WARN, serialize(e)));
  }

  public void error(Throwable e) {
    myPullingQueue.add(new ServerLogEvent(ServerLogEvent.Type.ERROR, serialize(e)));
  }

  public void print(String o) {
    myPullingQueue.add(new ServerLogEvent(ServerLogEvent.Type.PRINT, o));
  }

  @NotNull
  @Override
  public List<ServerLogEvent> pull() {
    return MavenRemotePullUtil.pull(myPullingQueue);
  }

  private String serialize(Throwable e){
    return ExceptionUtilRt.getThrowableText(wrapException(e), "");
  }
}
