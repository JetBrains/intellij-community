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
package com.jetbrains.python.commandInterface.chunkDriverBasedPresenter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Pack of {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkInfo} for certain chunks and other parsing info.
 *
 * @author Ilya.Kazakevich
 */
public final class ParseInfo {
  @Nullable
  private final String myStatusText;
  @NotNull
  private final List<ChunkInfo> myChunkInfo = new ArrayList<ChunkInfo>();
  @Nullable
  private final Runnable myExecutor;

  /**
   * @param chunkInfo  Chunk info should match chunks in 1-to-1 manner:
   *                   If "chunk1 chunk2 chunk3" were provided, you then need to return list where first chunkInfo matches first chunk ets.
   *                   And there also should be one more chunkInfo for tail.
   * @param statusText Status text {@link com.jetbrains.python.commandInterface.CommandInterfaceView view} may display.
   * @param executor   Engine to process command-line execution
   */
  public ParseInfo(@NotNull final Collection<ChunkInfo> chunkInfo,
                   @Nullable final String statusText,
                   @Nullable final Runnable executor) {
    myStatusText = statusText;
    myChunkInfo.addAll(chunkInfo);
    myExecutor = executor;
  }

  /**
   * Simple parse info with out of status text and executor
   *
   * @param chunkInfo Chunk info (See {@link #ParseInfo(java.util.Collection, String, Runnable)}
   * @see #ParseInfo(java.util.Collection, String, Runnable)
   */
  public ParseInfo(@NotNull final Collection<ChunkInfo> chunkInfo) {
    this(chunkInfo, null, null);
  }

  /**
   * @return Status text {@link com.jetbrains.python.commandInterface.CommandInterfaceView view} may display.
   */
  @Nullable
  String getStatusText() {
    return myStatusText;
  }

  /**
   * @return Engine to process command-line execution
   */
  @Nullable
  Runnable getExecutor() {
    return myExecutor;
  }

  /**
   * @return Chunk info should match chunks in 1-to-1 manner, and there also should be one more chunkInfo for tail (
   * see {@link #ParseInfo(java.util.Collection, String, Runnable) ctor} manual)
   */
  @NotNull
  List<ChunkInfo> getChunkInfo() {
    return Collections.unmodifiableList(myChunkInfo);
  }
}
