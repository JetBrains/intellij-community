/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.suggestionList;

import org.jetbrains.annotations.NotNull;

/**
 * Suggestion to display to user
 *
 * @author Ilya.Kazakevich
 */
public class Suggestion {
  @NotNull
  private final String myText;
  private final boolean myStrong;

  /**
   * @param text   text to display
   * @param strong is strong or not. Strong element will be marked somehow when displayed.
   */
  public Suggestion(@NotNull final String text, final boolean strong) {
    myText = text;
    myStrong = strong;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public boolean isStrong() {
    return myStrong;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Suggestion)) return false;

    Suggestion that = (Suggestion)o;

    if (myStrong != that.myStrong) return false;
    if (!myText.equals(that.myText)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myText.hashCode();
    result = 31 * result + (myStrong ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Suggestion{" +
           "myText='" + myText + '\'' +
           ", myStrong=" + myStrong +
           '}';
  }
}
