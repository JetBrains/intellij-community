/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.remote;

import com.intellij.remote.ColoredRemoteProcessHandler;
import com.intellij.remote.RemoteProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public abstract class PyRemoteProcessHandlerBase extends ColoredRemoteProcessHandler<RemoteProcess> implements PyRemoteProcessControl {
  public PyRemoteProcessHandlerBase(@NotNull RemoteProcess process,
                                    @NotNull String commandLine,
                                    @Nullable Charset charset) {
    super(process, commandLine, charset);
  }
}
