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

import com.jetbrains.python.WordWithPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chunk and its info pair. Chunk may be null, while chunk info always present.
 * @author Ilya.Kazakevich
 */
final class ChunkAndInfo implements Comparable<ChunkAndInfo> {
  @Nullable
  private final WordWithPosition myChunk;
  @NotNull
  private final ChunkInfo myChunkInfo;

  ChunkAndInfo(@Nullable final WordWithPosition chunk, @NotNull final ChunkInfo chunkInfo) {
    myChunk = chunk;
    myChunkInfo = chunkInfo;
  }

  /**
   * @return chunk (word). may be null
   */
  @Nullable
  WordWithPosition getChunk() {
    return myChunk;
  }

  /**
   * @return chunk info.
   */
  @NotNull
  ChunkInfo getChunkInfo() {
    return myChunkInfo;
  }

  @Override
  public int compareTo(@NotNull final ChunkAndInfo o) {
    if (myChunk == null && o.myChunk == null) {
      return 0;
    }
    if (myChunk == null) {
      return 1;
    }
    if (o.myChunk == null) {
      return -1;
    }
    return myChunk.getFrom().compareTo(o.myChunk.getFrom());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ChunkAndInfo info = (ChunkAndInfo)o;

    if (myChunk != null ? !myChunk.equals(info.myChunk) : info.myChunk != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myChunk != null ? myChunk.hashCode() : 0;
  }
}
