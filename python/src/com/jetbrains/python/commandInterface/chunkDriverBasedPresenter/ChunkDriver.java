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

import java.util.List;

/**
 * Driver that knows how to parse pack of chunks into chunk info.
 *
 * @author Ilya.Kazakevich
 */
public interface ChunkDriver {
  /**
   * Parses chunks into pack of chunks. There <strong>always</strong> should be chunk+1 chunkInfos (one for the tail like
   * {@link com.jetbrains.python.commandInterface.CommandInterfaceView#AFTER_LAST_CHARACTER_RANGE}).
   * So, at least one chunk info should also exist!
   *
   * @param chunks chunks (parts of command line)
   * @return parse info with chunks info. Warning: do not return less chunk infos than chunks provided. That leads to runtime error
   */
  @NotNull
  ParseInfo parse(@NotNull List<WordWithPosition> chunks);
}
