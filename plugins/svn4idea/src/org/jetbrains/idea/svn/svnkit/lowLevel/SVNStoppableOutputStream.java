/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit.lowLevel;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/30/12
 * Time: 5:41 PM
 */
public class SVNStoppableOutputStream extends OutputStream {
  private final OutputStream myLogStream;

  public SVNStoppableOutputStream(final OutputStream logStream) {
    myLogStream = logStream;
  }

  @Override
  public void write(int b) throws IOException {
    check();
    myLogStream.write(b);
  }

  @Override
  public void close() throws IOException {
    check();
    myLogStream.close();
  }

  @Override
  public void flush() throws IOException {
    check();
    myLogStream.flush();
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    check();
    myLogStream.write(b, off, len);
  }

  private void check() throws IOException {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null && indicator.isCanceled()) {
      throw new IOException("Write request to canceled by user");
    }
  }
}
