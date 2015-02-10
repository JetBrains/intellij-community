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

import com.intellij.util.Range;
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandInterface.CommandInterfacePresenterAdapter;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// TODO: Test

/**
 * Presenter that uses {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriver} to parse pack of chunks
 * to obtain {@link com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkInfo}.
 * Each chunk may be paired with certain chunk info. Such info tells presenter whether this chunk has info, error, suggestions and so on.
 * If caret situated far from chunks, then next neariest chunk should be found (see {@link #findNearestChunkAndInfo()}.
 *
 *
 * @author Ilya.Kazakevich
 * @see com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkInfo
 */
public final class ChunkDriverBasedPresenter extends CommandInterfacePresenterAdapter {

  @NotNull
  private final ChunkDriver myChunkDriver;
  @NotNull
  private final SortedSet<ChunkAndInfo> myChunkAndInfos = new TreeSet<ChunkAndInfo>();
  @Nullable
  private Runnable myExecutor;

  public ChunkDriverBasedPresenter(@NotNull final CommandInterfaceView view,
                                   @NotNull final ChunkDriver chunkDriver) {
    super(view);
    myChunkDriver = chunkDriver;
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
    final List<WordWithPosition> chunks = WordWithPosition.splitText(myView.getText());
    final ParseInfo parseInfo = myChunkDriver.parse(chunks);
    myExecutor = parseInfo.getExecutor();
    final List<ChunkInfo> chunkInfos = parseInfo.getChunkInfo();
    assert chunkInfos.size() >= chunks.size() : "Driver did not return enough chunks";
    assert !chunkInfos.isEmpty() : "At least one chunk info should exist";
    myChunkAndInfos.clear();
    for (int i = 0; i < chunkInfos.size(); i++) {
      final ChunkInfo chunkInfo = chunkInfos.get(i);
      final WordWithPosition chunk = chunks.size() > i ? chunks.get(i) : null;
      myChunkAndInfos.add(new ChunkAndInfo(chunk, chunkInfo));
    }


    // configure Errors And Balloons

    final Collection<WordWithPosition> infoBalloons = new ArrayList<WordWithPosition>();
    final Collection<WordWithPosition> errorBalloons = new ArrayList<WordWithPosition>();


    for (final ChunkAndInfo chunkInfoPair : myChunkAndInfos) {
      final ChunkInfo chunkInfo = chunkInfoPair.getChunkInfo();
      Range<Integer> chunk = chunkInfoPair.getChunk();
      if (chunk == null) {
        // After the last!
        chunk = CommandInterfaceView.AFTER_LAST_CHARACTER_RANGE;
      }
      final String error = chunkInfo.getError();
      if (error != null) {
        errorBalloons.add(new WordWithPosition(error, chunk));
      }
      final String info = chunkInfo.getInfoBalloon();
      if (info != null) {
        infoBalloons.add(new WordWithPosition(info, chunk));
      }
    }

    myView.setInfoAndErrors(infoBalloons, errorBalloons);


    if (!skipSuggestions) {
      configureSuggestion(false);
    }

    // Configure subtexts
    final List<Range<Integer>> placesWhereSuggestionAvailable = new ArrayList<Range<Integer>>();
    for (final ChunkAndInfo chunkAndInfo : myChunkAndInfos) {
      Range<Integer> chunk = chunkAndInfo.getChunk();
      // If some place has suggestions, then add it
      if (chunkAndInfo.getChunkInfo().getSuggestions() != null) {
        if (chunk == null) {
          // If there is no such chunk, that means we are after the last character, so use "special case" here
          //noinspection ReuseOfLocalVariable
          chunk = CommandInterfaceView.AFTER_LAST_CHARACTER_RANGE;
        }
        placesWhereSuggestionAvailable.add(chunk);
      }
    }
    myView.configureSubTexts(parseInfo.getStatusText(), placesWhereSuggestionAvailable);
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
    final ChunkAndInfo chunkAndInfo = findNearestChunkAndInfo();
    final ChunkInfo chunkInfo = chunkAndInfo.getChunkInfo();
    final WordWithPosition chunk = chunkAndInfo.getChunk();

    final SuggestionInfo suggestionInfo = chunkInfo.getSuggestions();
    if (suggestionInfo == null || (!suggestionInfo.isShowSuggestionsAutomatically() && !requestedExplicitly)) {
      return;
    }
    final List<String> suggestions = new ArrayList<String>(suggestionInfo.getSuggestions());
    if (chunk != null && !requestedExplicitly) {
      filterLeaveOnlyMatching(suggestions, chunk.getText());
    }
    // TODO: Place to add history
    if (!suggestions.isEmpty()) {
      // No need to display empty suggestions
      myView
        .displaySuggestions(new SuggestionsBuilder(suggestions), suggestionInfo.isShowAbsolute(), (chunk == null ? null : chunk.getText()));
    }
  }


  /**
   * Filters collection of suggestions leaving only those starts with certain text.
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
   * Searches for the nearest chunk and info to use. It may or may not find chunk, but it should always provide some chunk info.
   *
   * @return nearest chunk info and, probably, chunk.
   */
  @NotNull
  private ChunkAndInfo findNearestChunkAndInfo() {
    final int caretPosition = myView.getCaretPosition();

    for (final ChunkAndInfo chunkAndInfo : myChunkAndInfos) {
      final Range<Integer> range = chunkAndInfo.getChunk();
      if (range != null && range.isWithin(caretPosition)) {
        return chunkAndInfo;
      }
      if (range != null && range.getFrom() > caretPosition) {
        return new ChunkAndInfo(null, chunkAndInfo.getChunkInfo());
      }
    }

    return new ChunkAndInfo(null, myChunkAndInfos.last().getChunkInfo());
  }


  @Override
  public void completionRequested(@Nullable final String valueFromSuggestionList) {
    final ChunkAndInfo chunkAndInfo = findNearestChunkAndInfo();
    final WordWithPosition chunk = chunkAndInfo.getChunk();

    if (valueFromSuggestionList != null) {
      // Just insert it
      if (chunk != null) { // If caret is on the chunk itself
        myView.replaceText(chunk.getFrom(), chunk.getTo(), valueFromSuggestionList);
      }
      else {
        myView.insertTextAfterCaret(valueFromSuggestionList);
      }
      return;
    }

    //User did not provide text no insert, do our best to find one

    final ChunkInfo chunkInfo = chunkAndInfo.getChunkInfo();
    final SuggestionInfo suggestionInfo = chunkInfo.getSuggestions();
    if (suggestionInfo == null) {
      return; // No suggestion available for this chunk
    }
    final List<String> suggestions = new ArrayList<String>(suggestionInfo.getSuggestions());
    if (chunk != null) {
      filterLeaveOnlyMatching(suggestions, chunk.getText());
    }
    if (suggestions.size() == 1) {
      // Exclusive!
      if (chunk != null) {
        myView.replaceText(chunk.getFrom(), chunk.getTo(), suggestions.get(0));
      }
      else {
        myView.insertTextAfterCaret(suggestions.get(0));
      }
    }
  }

  @Override
  public void executionRequested() {
    if (myExecutor == null) {
      // TODO: Display error somehow
    }
    else {
      myExecutor.run();
    }
  }
}
