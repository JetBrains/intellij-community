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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.commandInterface.command.*;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import com.jetbrains.python.PythonTestUtil;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared tools to testing command line
 *
 * @author Ilya.Kazakevich
 */
final class CommandTestTools {
  @NotNull
  static final String TEST_PATH = PythonTestUtil.getTestDataPath() + "/commandLine/";

  private CommandTestTools() {
  }

  /**
   * <pre>command --available-option=available_argument --option-no-argument positional_argument</pre>
   *
   * @return list cosists of fake command with opts and arguments
   */
  @TestOnly
  @NotNull
  static List<Command> createCommands() {
    //command positional_argument --available-option=available_argument
    final Command command = EasyMock.createMock(Command.class);
    EasyMock.expect(command.getName()).andReturn("command").anyTimes();
    EasyMock.expect(command.getHelp(true)).andReturn(new Help("some_text")).anyTimes();
    EasyMock.expect(command.getHelp(false)).andReturn(new Help("some_text")).anyTimes();
    final List<Option> options = new ArrayList<>();


    final Pair<List<String>, Boolean> argument = Pair.create(Collections.singletonList("available_argument"), true);
    options.add(new Option(Pair.create(1, new Argument(new Help("option argument"), argument)), new Help(""),
                                       Collections.<String>emptyList(),
                                       Collections.singletonList("--available-option")));


    options.add(new Option(null, new Help(""),
                           Collections.<String>emptyList(),
                           Collections.singletonList("--option-no-argument")));

    EasyMock.expect(command.getOptions()).andReturn(options).anyTimes();


    final ArgumentsInfo argumentInfo = new KnownArgumentsInfo(Collections.singletonList(
      new Argument(new Help("positional_argument"),
                   Pair.create(Collections.singletonList("positional_argument"), true))), 1, 1);

    EasyMock.expect(command.getArgumentsInfo()).andReturn(argumentInfo)
      .anyTimes();

    EasyMock.replay(command);
    return Collections.singletonList(command);
  }

  /**
   * Hack to register file type (not registered for some reason?)
   */
  static void initFileType() {
    ApplicationManager.getApplication().runWriteAction(() -> FileTypeManager.getInstance().associateExtension(CommandLineFileType.INSTANCE, CommandLineFileType.EXTENSION));
  }

  /**
   * Creates command file by text and  {@link #createCommands() fills it with commands}
   * @param testFixture fixture
   * @param text command text
   * @return command file
   * @see #createCommands()
   */
  @NotNull
  static CommandLineFile createFileByText(@NotNull final CodeInsightTestFixture testFixture, @NotNull final String text) {
    final CommandLineFile file =
      (CommandLineFile)testFixture.configureByText(CommandLineFileType.INSTANCE, text);
    file.setCommands(createCommands());
    return file;
  }
}
