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
package com.jetbrains.python.commandInterface.commandBasedChunkDriver;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkDriver;
import com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ChunkInfo;
import com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.ParseInfo;
import com.jetbrains.python.commandInterface.chunkDriverBasedPresenter.SuggestionInfo;
import com.jetbrains.python.commandInterface.command.Argument;
import com.jetbrains.python.commandInterface.command.ArgumentsInfo;
import com.jetbrains.python.commandInterface.command.Command;
import com.jetbrains.python.commandLineParser.CommandLineParseResult;
import com.jetbrains.python.commandLineParser.CommandLineParser;
import com.jetbrains.python.commandLineParser.CommandLinePartType;
import com.jetbrains.python.commandLineParser.MalformedCommandLineException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Chunk driver that uses pack of commands.
 *
 * @author Ilya.Kazakevich
 */
public final class CommandBasedChunkDriver implements ChunkDriver {
  @NotNull
  private final CommandLineParser myCommandLineParser;
  @NotNull
  private final Map<String, Command> myCommands = new TreeMap<String, Command>(); // To sort commands by name
  @NotNull
  private final Module myModule;

  /**
   * @param commandLineParser parser to use
   * @param module            module parsing takes place in
   * @param commands          available commands
   */
  public CommandBasedChunkDriver(@NotNull final CommandLineParser commandLineParser,
                                 @NotNull final Module module,
                                 @NotNull final Collection<? extends Command> commands) {
    myCommandLineParser = commandLineParser;
    for (final Command command : commands) {
      myCommands.put(command.getName(), command);
    }
    myModule = module;
  }

  @Override
  @NotNull
  public ParseInfo parse(@NotNull final List<WordWithPosition> chunks) {
    // TODO: Refactor to add command first to prevent copy/paste
    if (chunks.isEmpty()) {
      return createBadCommandInfo(chunks.size());
    }

    try {
      final CommandLineParseResult commandLine = myCommandLineParser.parse(chunks);
      final Command command = myCommands.get(commandLine.getCommand().getText());
      if (command == null) {
        // Bad command inserted
        return createBadCommandInfo(chunks.size());
      }

      // Command exists, lets check its arguments

      // TODO: Support options as well

      // First, validate values
      final ArgumentsInfo commandArgumentsInfo = command.getArgumentsInfo();


      final List<ChunkInfo> chunkInfo = new ArrayList<ChunkInfo>();
      // First chunk iscommand and it seems to be ok
      chunkInfo.add(new ChunkInfo(null, null, new SuggestionInfo(false, true, myCommands.keySet())));


      // Now add balloons, info and suggestions
      for (int i = 0; i < commandLine.getParts().size(); i++) {
        final Pair<Boolean, Argument> argumentPair = commandArgumentsInfo.getArgument(i);
        if (argumentPair == null) { // Excess argument!
          chunkInfo.add(new ChunkInfo(null, PyBundle.message("commandLine.validation.excessArg")));
          continue;
        }
        final Argument argument = argumentPair.getSecond();
        final List<String> availableValues = argument.getAvailableValues();
        final Pair<CommandLinePartType, WordWithPosition> part = commandLine.getParts().get(i);
        if (part.first != CommandLinePartType.ARGUMENT) {
          // Only arguments are supported now, so we have nothing to say about this chunk
          chunkInfo.add(new ChunkInfo(null, null));
        }
        final String argumentValue = part.second.getText();
        String errorMessage = null;
        if (availableValues != null && !availableValues.contains(argumentValue)) {
          // Bad value
          errorMessage = PyBundle.message("commandLine.validation.argBadValue");
        }
        // Argument  seems to be ok. We suggest values automatically only if value is bad
        chunkInfo.add(new ChunkInfo(argument.getHelpText(), errorMessage,
                                    (availableValues != null ? new SuggestionInfo(errorMessage != null, false, availableValues) : null)));
      }


      final Pair<Boolean, Argument> nextArgumentPair = commandArgumentsInfo.getArgument(commandLine.getParts().size());
      if (nextArgumentPair != null) {
        // Next arg exists
        final Argument nextArgument = nextArgumentPair.getSecond();
        final List<String> availableValues = nextArgument.getAvailableValues();
        // Only add error if required
        final String error = nextArgumentPair.first ? PyBundle.message("commandLine.validation.argMissing") : null;
        final ChunkInfo lastArgInfo =
          new ChunkInfo(nextArgument.getHelpText(), error,
                        (availableValues != null ? new SuggestionInfo(false, false, availableValues) : null));
        chunkInfo.add(lastArgInfo);
      }
      else {
        // Looks like all arguments are satisfied. Adding empty chunk to prevent completion etc.
        // This is a hack, but with out of it last chunkinfo will always be used, even 200 chars after last place
        chunkInfo.add(new ChunkInfo(null, null));
      }

      assert chunkInfo.size() >= chunks.size() : "Contract broken: not enough chunks";

      return new ParseInfo(chunkInfo, command.getHelp(), new MyExecutor(command, commandLine));
    }
    catch (final MalformedCommandLineException ignored) {
      // Junk enetered!
      return createBadCommandInfo(chunks.size());
    }
  }


  /**
   * Creates parse info signaling command is bad or junk
   *
   * @param numberOfChunks number of chunks provided by user (we must return chunk info for each chunk + 1, accroding to contract)
   * @return parse info to return
   */
  @NotNull
  private ParseInfo createBadCommandInfo(final int numberOfChunks) {
    final List<ChunkInfo> result = new ArrayList<ChunkInfo>();
    // We know that first chunk command line, but we can't say anything about outher chunks except they are bad.
    // How ever, we must say something according to contract (number of infos should be equal or greater than number of chunks)
    result
      .add(new ChunkInfo(null, PyBundle.message("commandLine.validation.badCommand"), new SuggestionInfo(true, true, myCommands.keySet())));
    for (int i = 1; i < numberOfChunks; i++) {
      result.add(
        new ChunkInfo(null, PyBundle.message("commandLine.validation.badCommand")));
    }

    return new ParseInfo(result);
  }


  /**
   * Adapter that executes command using {@link Command#execute(com.intellij.openapi.module.Module, com.jetbrains.python.commandLineParser.CommandLineParseResult)}
   */
  private class MyExecutor implements Runnable {
    @NotNull
    private final Command myCommand;
    @NotNull
    private final CommandLineParseResult myCommandLine;

    MyExecutor(@NotNull final Command command, @NotNull final CommandLineParseResult line) {
      myCommand = command;
      myCommandLine = line;
    }

    @Override
    public void run() {
      myCommand.execute(myModule, myCommandLine);
    }
  }
}
