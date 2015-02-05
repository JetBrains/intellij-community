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
package com.jetbrains.python.commandInterface.commandsWithArgs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.commandInterface.CommandInterfacePresenterAdapter;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import com.jetbrains.python.commandInterface.CommandInterfaceView.SpecialErrorPlace;
import com.jetbrains.python.optParse.MalformedCommandLineException;
import com.jetbrains.python.optParse.ParsedCommandLine;
import com.jetbrains.python.optParse.WordWithPosition;
import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Command-line interface presenter that is command-based
 *
 * @param <C> Command type
 * @author Ilya.Kazakevich
 */
public class CommandInterfacePresenterCommandBased<C extends Command> extends CommandInterfacePresenterAdapter {
  /**
   * [name] -> command. Linked is used to preserve order.
   */
  private final Map<String, C> myCommands = new LinkedHashMap<String, C>();
  /**
   * currenly used strategy (see interface for more info)
   */
  private Strategy myStrategy;


  /**
   * @param view     view
   * @param commands available commands
   */
  public CommandInterfacePresenterCommandBased(@NotNull final CommandInterfaceView view,
                                               @NotNull final Iterable<C> commands) {
    super(view);
    for (final C command : commands) {
      myCommands.put(command.getName(), command);
    }
  }

  /**
   * @param view     view
   * @param commands available commands
   */
  public CommandInterfacePresenterCommandBased(@NotNull final CommandInterfaceView view,
                                               @NotNull final C... commands) {
    this(view, Arrays.asList(commands));
  }

  @Override
  public void launch() {
    /*myView.setPreferredWidthInChars(getMaximumCommandWithArgsLength());*/
    super.launch();
    myStrategy = new NoCommandStrategy(this);
  }

  @Override
  public void textChanged(final boolean inForcedTextMode) {
    configureStrategy();
    myView.setSubText(myStrategy.getSubText());
    final Pair<SpecialErrorPlace, List<WordWithPosition>> errorInfo = myStrategy.getErrorInfo();
    myView.showErrors(errorInfo.getSecond(), errorInfo.first);
    myView.setBalloons(myStrategy.getBalloonsToShow());

    final SuggestionInfo suggestionInfo = myStrategy.getSuggestionInfo();
    final List<String> suggestions = new ArrayList<String>(suggestionInfo.getSuggestions());

    final String lastPart = getLastPart();
    if ((lastPart != null) && myStrategy.isUnknownTextExists()) {
      //Filter to starts from
      final Iterator<String> iterator = suggestions.iterator();
      while (iterator.hasNext()) {
        final String textToCheck = iterator.next();

        if (!textToCheck.startsWith(lastPart)) {
          iterator.remove();
        }
      }
    }

    if (!suggestionInfo.myShowOnlyWhenRequested && !suggestions.isEmpty()) {
      final SuggestionsBuilder suggestionsBuilder = getBuilderWithHistory();
      suggestionsBuilder.add(suggestions);

      myView
        .displaySuggestions(suggestionsBuilder, suggestionInfo.myAbsolute, null);
    }
    else {
      myView.removeSuggestions();
    }
  }

  /**
   * @return builder that already has history in its prefix group (see {@link com.jetbrains.python.suggestionList.SuggestionsBuilder})
   */
  @NotNull
  private SuggestionsBuilder getBuilderWithHistory() {
    return new SuggestionsBuilder();

    // TODO: Uncomment when history would be fixed
    /*final SuggestionsBuilder suggestionsBuilder = new SuggestionsBuilder();
    final List<CommandExecutionInfo> history = getHistory();
    final Collection<String> historyCommands = new LinkedHashSet<String>();
    for (final CommandExecutionInfo info : history) {
      historyCommands.add(info.toString());
    }

    if (!historyCommands.isEmpty()) {
      // TODO: Later implement folding by name
      suggestionsBuilder.changeGroup(false);
      suggestionsBuilder
        .add(ArrayUtil.toStringArray(historyCommands));
      suggestionsBuilder.changeGroup(true);
    }

    return suggestionsBuilder;*/
  }

  /**
   * @return execution info from history. It is empty by default, child should implement it.
   */
  @NotNull
  protected List<CommandExecutionInfo> getHistory() {
    return Collections.emptyList();
  }

  /**
   * @return command that entered in box, or null of just entered
   */
  @Nullable
  protected CommandExecutionInfo getCommandToExecute() {
    return myStrategy.getCommandToExecute();
  }

  /**
   * Finds and sets appropriate strategy
   */
  private void configureStrategy() {
    final ParsedCommandLine line = getParsedCommandLine();
    if (line != null) {
      final Command command = myCommands.get(line.getCommand().getText());
      if (command != null) {
        myStrategy = new InCommandStrategy(command, line, this);
        return;
      }
    }
    myStrategy = new NoCommandStrategy(this); // No command or bad command found
  }

  @Override
  public void completionRequested(@Nullable final String valueFromSuggestionList) {
    if (valueFromSuggestionList != null) {
      final SuggestionInfo suggestionInfo = myStrategy.getSuggestionInfo();
      if (suggestionInfo.getSuggestions().contains(valueFromSuggestionList)) {
        final ParsedCommandLine commandLine = getParsedCommandLine();
        final List<String> words = commandLine != null ? commandLine.getAsWords() : new ArrayList<String>();
        if (!words.isEmpty() && myView.isCaretOnWord()) {
          words.remove(words.size() - 1);
        }
        words.add(valueFromSuggestionList);
        myView.forceText(StringUtil.join(words, " "));
      }
    }
    myView.removeSuggestions();
  }

  @Override
  public void suggestionRequested() {
    final SuggestionInfo suggestionInfo = myStrategy.getSuggestionInfo();
    final List<String> suggestions = suggestionInfo.getSuggestions();
    if (!suggestions.isEmpty()) {
      final SuggestionsBuilder suggestionsBuilder = getBuilderWithHistory();
      suggestionsBuilder.add(suggestions);
      myView.displaySuggestions(suggestionsBuilder, suggestionInfo.myAbsolute, null);
    }
  }

  @Override
  public void executionRequested(@Nullable final String valueFromSuggestionList) {

  }

  /**
   * @return [command_name => command] all available commands
   */
  @NotNull
  protected final Map<String, C> getCommands() {
    return Collections.unmodifiableMap(myCommands);
  }

  /**
   * @return parsed commandline entered by user
   */
  @Nullable
  final ParsedCommandLine getParsedCommandLine() {
    try {
      return new ParsedCommandLine(myView.getText());
    }
    catch (final MalformedCommandLineException ignored) {
      return null;
    }
  }


  /**
   * @return last part of splitted text (if any). I.e. "foo bar spam" will return "spam"
   */
  @Nullable
   final String getLastPart() {
    final ParsedCommandLine commandLine = getParsedCommandLine();
    if (commandLine == null || commandLine.getAsWords().isEmpty()) {
      return null;
    }
    final List<String> words = commandLine.getAsWords();
    return words.get(words.size() - 1);
  }

  /**
   * @return view
   */
  @NotNull
  CommandInterfaceView getView() {
    return myView;
  }

}
