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

import org.jetbrains.annotations.Nullable;

/**
 * Information about certain place in text, provided by chunk driver.
 *
 * @author Ilya.Kazakevich
 */
public final class ChunkInfo {
  @Nullable
  private final String myInfoBalloon;
  @Nullable
  private final String myError;
  @Nullable
  private final SuggestionInfo mySuggestions;


  public ChunkInfo(@Nullable final String infoBalloon,
                   @Nullable final String error) {
    this(infoBalloon, error, null);
  }


  /**
   *
   * @param infoBalloon Info balloon to display when caret meets this place (null if display nothing)
   * @param error Error balloon to display when caret meets this place and underline text as error (null if no error)
   * @param suggestions list of suggestions available in this place (if any)
   */
  public ChunkInfo(@Nullable final String infoBalloon,
                   @Nullable final String error,
                   @Nullable final SuggestionInfo suggestions) {
    myInfoBalloon = infoBalloon;
    myError = error;
    mySuggestions = suggestions;
  }


  /**
   *
   * @return Info balloon to display when caret meets this place (null if display nothing)
   */
  @Nullable
  public String getInfoBalloon() {
    return myInfoBalloon;
  }

  /**
   *
   * @return Error balloon to display when caret meets this place and underline text as error (null if no error)
   */
  @Nullable
  public String getError() {
    return myError;
  }

  /**
   *
   * @return list of suggestions available in this place (if any)
   */
  @Nullable
  public SuggestionInfo getSuggestions() {
    return mySuggestions;
  }
}
