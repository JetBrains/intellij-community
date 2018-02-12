/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BinaryOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnProcessHandler extends OSProcessHandler {

  private final boolean myForceUtf8;
  private final boolean myForceBinary;
  @NotNull private final ByteArrayOutputStream myBinaryOutput;

  public SvnProcessHandler(@NotNull Process process, @NotNull String commandLine, boolean forceUtf8, boolean forceBinary) {
    super(process, commandLine);

    myForceUtf8 = forceUtf8;
    myForceBinary = forceBinary;
    myBinaryOutput = new ByteArrayOutputStream();
  }

  @NotNull
  public ByteArrayOutputStream getBinaryOutput() {
    return myBinaryOutput;
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return myForceUtf8 ? CharsetToolkit.UTF8_CHARSET : super.getCharset();
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

  private class SimpleBinaryOutputReader extends BinaryOutputReader {
    private SimpleBinaryOutputReader(@NotNull InputStream stream, @NotNull SleepingPolicy sleepingPolicy) {
      super(stream, sleepingPolicy);
      start(myPresentableName);
    }

    @Override
    protected void onBinaryAvailable(@NotNull byte[] data, int size) {
      myBinaryOutput.write(data, 0, size);
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return SvnProcessHandler.this.executeTask(runnable);
    }
  }
}
