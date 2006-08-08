/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.text;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LineReader {
  private int myPos = -1;
  private int[] myBuffer = new int[2];
  private final InputStream myInputStream;
  private boolean myAtEnd = false;

  public LineReader(InputStream in) {
    myInputStream = in;
  }

  public List<byte[]> readLines() throws IOException {

    ArrayList<byte[]> result = new ArrayList<byte[]>();
    byte[] line;
    while ((line = readLineInternal()) != null) result.add(line);
    return result;
  }

  private int read() throws IOException {
    if (myPos >= 0) {
      int result = myBuffer[myPos];
      myPos--;
      return result;
    }
    return myInputStream.read();
  }

  private class ReadLine {
    private String myCurrentEOL = "";
    private ByteArrayOutputStream myResult = null;

    @Nullable
    public byte[] execute() throws IOException {

      if (myAtEnd) return null;

      synchronized (myInputStream) {
        while (true) {
          int ch = read();
          if (ch < 0)
            return processEndOfStream();
          if (notLineSeparator(ch)) {
            if (myCurrentEOL.equals("\r")) {
              unread(ch);
              return getResult();
            } else if (myCurrentEOL.equals("\r\r")) {
              unread(ch);
              unread('\r');
              return getResult();
            } else {
              appendToResult(ch);
              continue;
            }
          }
          if (ch == '\r') {
            if (myCurrentEOL.length() == 0 || myCurrentEOL.equals("\r")) {
              myCurrentEOL += "\r";
            } else if (myCurrentEOL.equals("\r\r")) {
              unread('\r');
              unread('\r');
              return getResult();
            }
            continue;
          }
          if (ch == '\n') {
            return getResult();
          }
        }
      }
    }

    private boolean notLineSeparator(int ch) {
      return ch != '\r' && ch != '\n';
    }

    private void appendToResult(int ch) {
      createResult();
      myResult.write(ch);
    }

    private byte[] getResult() {
      createResult();
      try {
        myResult.flush();
      } catch (IOException e) {
        //ignore
      }

      return myResult.toByteArray();
    }

    private void createResult() {
      if (myResult == null) myResult = new ByteArrayOutputStream();
    }

    private byte[] processEndOfStream() {
      myAtEnd = true;
      return getResult();
    }
  }

  @Nullable
  private byte[] readLineInternal() throws IOException {
    return new ReadLine().execute();
  }

  private void unread(int b) throws IOException {
    myPos++;
    if (myPos >= myBuffer.length)
      throw new IOException("Push back buffer is full");
    myBuffer[myPos] = b;

  }
}
