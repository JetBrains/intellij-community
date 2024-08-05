// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BinaryOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class SvnProcessHandler extends OSProcessHandler {

  private final List<String> myCommandLineList;
  private final boolean myForceUtf8;
  private final boolean myForceBinary;
  @NotNull private final ByteArrayOutputStream myBinaryOutput;

  public SvnProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine, boolean forceUtf8, boolean forceBinary) {
    super(process, commandLine.getCommandLineString());

    myCommandLineList = getSafeCommandList(commandLine);

    myForceUtf8 = forceUtf8;
    myForceBinary = forceBinary;
    myBinaryOutput = new ByteArrayOutputStream();
  }

  @Override
  public String getCommandLineForLog() {
    return ParametersListUtil.join(myCommandLineList);
  }

  @NotNull
  public ByteArrayOutputStream getBinaryOutput() {
    return myBinaryOutput;
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return myForceUtf8 ? StandardCharsets.UTF_8 : super.getCharset();
  }

  @NotNull
  @Override
  protected BaseDataReader createOutputDataReader() {
    if (myForceBinary) {
      return new SimpleBinaryOutputReader(myProcess.getInputStream(), readerOptions().policy());
    }
    else {
      return super.createOutputDataReader();
    }
  }

  private static @NotNull List<String> getSafeCommandList(@NotNull GeneralCommandLine commandLine) {
    List<String> commands = new ArrayList<>(commandLine.getCommandLineList(null));
    int index = commands.lastIndexOf("--password");
    if (index >= 0 && index + 1 < commands.size() - 1) {
      commands.set(index + 1, "<redacted>");
    }
    return commands;
  }

  private final class SimpleBinaryOutputReader extends BinaryOutputReader {
    private SimpleBinaryOutputReader(@NotNull InputStream stream, @NotNull SleepingPolicy sleepingPolicy) {
      super(stream, sleepingPolicy);
      start(myPresentableName);
    }

    @Override
    protected void onBinaryAvailable(byte @NotNull [] data, int size) {
      myBinaryOutput.write(data, 0, size);
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return SvnProcessHandler.this.executeTask(runnable);
    }
  }
}
