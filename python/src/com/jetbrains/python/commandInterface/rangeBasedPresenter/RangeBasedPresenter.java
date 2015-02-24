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

import com.intellij.openapi.util.Pair;
import com.intellij.util.Range;
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandInterface.CommandInterfacePresenterAdapter;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Presenter that uses {@link RangeInfoDriver} to obtain {@link RangeInfo info}.
 * Such info tells presenter whether this text has info, error, suggestions and so on.
 * If caret situated far from text, then next neariest range should be found (see {@link #findNearestRangeInfo()}.
 *
 * @author Ilya.Kazakevich
 * @see RangeInfo
 */
public final class RangeBasedPresenter extends CommandInterfacePresenterAdapter {
  @NotNull
  private final RangeInfoDriver myRangeInfoDriver;
  @Nullable
  private Executor myExecutor;
  private final SortedSet<RangeInfo> myRangeInfos = new TreeSet<RangeInfo>();

  public RangeBasedPresenter(@NotNull final CommandInterfaceView view,
                             @NotNull final RangeInfoDriver rangeInfoDriver) {
    super(view);
    myRangeInfoDriver = rangeInfoDriver;
  }

  @Override
  public void launch() {
    super.launch();
    reparseText(true);
  }

  @Override
  public void textChanged() {
    reparseText(false);
  }

  private void reparseText(final boolean skipSuggestions) {


    final Pair<Executor, List<RangeInfo>> rangeInfoStructure = myRangeInfoDriver.getCommandLineInfo(myView.getText());
    myExecutor = rangeInfoStructure.first;
    final List<RangeInfo> rangeInfos = rangeInfoStructure.second;

    assert !rangeInfos.isEmpty() : "At least one chunk info should exist";
    myRangeInfos.clear();
    myRangeInfos.addAll(rangeInfos);


    // configure Errors And Balloons

    final Collection<WordWithPosition> infoBalloons = new ArrayList<WordWithPosition>();
    final Collection<WordWithPosition> errorBalloons = new ArrayList<WordWithPosition>();


    for (final RangeInfo rangeInfo : rangeInfos) {
      final String error = rangeInfo.getError();
      if (error != null) {
        errorBalloons.add(new WordWithPosition(error, rangeInfo));
      }
      final String info = rangeInfo.getInfoBalloon();
      if (info != null) {
        infoBalloons.add(new WordWithPosition(info, rangeInfo));
      }
    }

    myView.setInfoAndErrors(infoBalloons, errorBalloons);


    if (!skipSuggestions) {
      configureSuggestion(false);
    }

    // Configure subtexts
    final List<Range<Integer>> placesWhereSuggestionAvailable = new ArrayList<Range<Integer>>();
    for (final RangeInfo rangeInfo : rangeInfos) {
      // If some place has suggestions, then add it
      if (rangeInfo.getSuggestions() != null) {
        placesWhereSuggestionAvailable.add(rangeInfo);
      }
    }
    final String statusText = (myExecutor != null ? myExecutor.getExecutionDescription() : null);
    myView.configureSubTexts(statusText, placesWhereSuggestionAvailable);
  }

  @Override
  public void suggestionRequested() {
    configureSuggestion(true); // Show or hide
  }


  /**
   * Displays suggestions if needed.
   *
   * @param requestedExplicitly is suggesions where requested by user explicitly or not
   */
  private void configureSuggestion(final boolean requestedExplicitly) {
    myView.removeSuggestions();
    final RangeInfo rangeInfo = findNearestRangeInfo();
    final String text = getTextByRange(rangeInfo);

    final SuggestionInfo suggestionInfo = rangeInfo.getSuggestions();
    if (suggestionInfo == null || (!suggestionInfo.isShowSuggestionsAutomatically() && !requestedExplicitly)) {
      return;
    }
    final List<String> suggestions = new ArrayList<String>(suggestionInfo.getSuggestions());
    if (text != null && !requestedExplicitly) {
      filterLeaveOnlyMatching(suggestions, text);
    }
    // TODO: Place to add history
    if (!suggestions.isEmpty()) {
      // No need to display empty suggestions
      myView
        .displaySuggestions(new SuggestionsBuilder(suggestions), suggestionInfo.isShowAbsolute(), text);
    }
  }


  /**
   * Filters collection of suggestions leaving only those starts with certain text.
   *
   * @param suggestions list to filter
   * @param textToMatch leave only parts that start with this param
   */
  private static void filterLeaveOnlyMatching(@NotNull final Iterable<String> suggestions, @NotNull final String textToMatch) {
    // TODO: use guava instead?
    final Iterator<String> iterator = suggestions.iterator();
    while (iterator.hasNext()) {
      if (!iterator.next().startsWith(textToMatch)) {
        iterator.remove();
      }
    }
  }

  /**
   * Searches for the nearest range to use.
   *
   * @return nearest range info
   */
  @NotNull
  private RangeInfo findNearestRangeInfo() {
    final int caretPosition = myView.getCaretPosition();

    for (final RangeInfo range : myRangeInfos) {
      if (range.isWithin(caretPosition)) {
        return range;
      }
      if (range.getFrom() > caretPosition) {
        return range; // Ranges are sorted, so we are on the next range. Take it, if caret is not within range
      }
    }

    return myRangeInfos.last();
  }


  @Override
  public void completionRequested(@Nullable final String valueFromSuggestionList) {
    final RangeInfo rangeInfo = findNearestRangeInfo();
    final String text = getTextByRange(rangeInfo);

    if (valueFromSuggestionList != null) {
      // Just insert it
      if (text != null) { // If caret is on the text itself
        // TODO: Replace next range, if it has no space before it(--a=12 should be replaced wth arg)
        myView.replaceText(rangeInfo.getFrom(), rangeInfo.getTo(), valueFromSuggestionList);
      }
      else {
        myView.insertTextAfterCaret(valueFromSuggestionList);
      }
      return;
    }

    //User did not provide text no insert, do our best to find one

    final SuggestionInfo suggestionInfo = rangeInfo.getSuggestions();
    if (suggestionInfo == null) {
      return; // No suggestion available for this chunk
    }
    final List<String> suggestions = new ArrayList<String>(suggestionInfo.getSuggestions());
    if (text != null) {
      filterLeaveOnlyMatching(suggestions, text);
    }
    if (suggestions.size() == 1) {
      // Exclusive!
      if (text != null) {
        // TODO: Replace next range, if it has no space before it(--a=12 should be replaced wth arg)
        myView.replaceText(rangeInfo.getFrom(), rangeInfo.getTo(), suggestions.get(0));
      }
      else {
        myView.insertTextAfterCaret(suggestions.get(0));
      }
    }
  }

  /**
   * Searches for text under the range
   * @param rangeInfo range
   * @return text or null if range does not contain any text
   */
  @Nullable
  private String getTextByRange(@NotNull final RangeInfo rangeInfo) {

    if (rangeInfo.isTerminationRange()) {
      return null;
    }
    else {
      final String viewText = myView.getText();
      return viewText.substring(rangeInfo.getFrom(), rangeInfo.getTo());
    }
  }

  @Override
  public void executionRequested() {
    if (myExecutor == null) {
      // TODO: Display error somehow
    }
    else {
      myExecutor.execute();
    }
  }
}
