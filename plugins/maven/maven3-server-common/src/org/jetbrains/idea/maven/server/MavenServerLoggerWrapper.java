/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import com.intellij.util.ExceptionUtilRt;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  @Override
  public List<ServerLogEvent> pull() {
    return MavenRemotePullUtil.pull(myPullingQueue);
  }

  private String serialize(Throwable e){
    return ExceptionUtilRt.getThrowableText(wrapException(e), "");
  }
}
