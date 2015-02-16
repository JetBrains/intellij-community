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
package com.jetbrains.python.commandInterface.rangeBasedPresenter;

import com.intellij.util.Range;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about certain place in text, provided by driver.
 *
 * @author Ilya.Kazakevich
 */
public final class RangeInfo extends Range<Integer> implements Comparable<RangeInfo> {


  /**
   * Special "terminator": the last range which is after all other ranges.
   */
  public static final Range<Integer> TERMINATION_RANGE = CommandInterfaceView.AFTER_LAST_CHARACTER_RANGE;

  @Nullable
  private final String myInfoBalloon;
  @Nullable
  private final String myError;
  @Nullable
  private final SuggestionInfo mySuggestions;
  private final boolean myExclusiveBorders;


  /**
   * @param infoBalloon      info to bind to this range
   * @param error            error to bind to this range
   * @param exclusiveBorders true if range has exclusive borders and right border is not part of it. I.e. 3 is part of 1-3 range with out
   *                         of exclusive borders, but not part of exclusive range
   * @param range            from and to
   */
  public RangeInfo(@Nullable final String infoBalloon,
                   @Nullable final String error,
                   final boolean exclusiveBorders,
                   @NotNull final Range<Integer> range) {
    this(infoBalloon, error, null, range, exclusiveBorders);
  }


  /**
   * @param infoBalloon Info balloon to display when caret meets this place (null if display nothing)
   * @param error       Error balloon to display when caret meets this place and underline text as error (null if no error)
   * @param suggestions list of suggestions available in this place (if any)
   */
  public RangeInfo(@Nullable final String infoBalloon,
                   @Nullable final String error,
                   @Nullable final SuggestionInfo suggestions,
                   @NotNull final Range<Integer> range,
                   final boolean exclusiveBorders) {
    super(range.getFrom(), range.getTo());
    myInfoBalloon = infoBalloon;
    myError = error;
    mySuggestions = suggestions;
    myExclusiveBorders = exclusiveBorders;
  }


  /**
   * @return Info balloon to display when caret meets this place (null if display nothing)
   */
  @Nullable
  public String getInfoBalloon() {
    return myInfoBalloon;
  }

  /**
   * @return Error balloon to display when caret meets this place and underline text as error (null if no error)
   */
  @Nullable
  public String getError() {
    return myError;
  }

  /**
   * @return list of suggestions available in this place (if any)
   */
  @Nullable
  public SuggestionInfo getSuggestions() {
    return mySuggestions;
  }

  @Override
  public int compareTo(RangeInfo o) {
    return getFrom().compareTo(o.getFrom());
  }

  @Override
  public boolean isWithin(final Integer object) {
    if (!super.isWithin(object)) {
      return false;
    }
    if (myExclusiveBorders) {
      return object < getTo();
    }
    return true;
  }

  public boolean isTerminationRange() {
    // TODO: copy/paste with view
    return TERMINATION_RANGE.getFrom().equals(getFrom()) && TERMINATION_RANGE.getTo().equals(getTo());
  }
}
