/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.util.text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LineReader {
  private int myPos = -1;
  private byte[] myBuffer = new byte[2];
  private InputStream myInputStream;
  private boolean myAtEnd = false;

  public LineReader() {

  }

  public List<byte[]> readLines(InputStream in) throws IOException {
    myInputStream = in;
    ArrayList<byte[]> result = new ArrayList<byte[]>();
    try {
      byte[] line;
      while ((line = readLineInternal()) != null) result.add(line);
    } finally {
      myInputStream.close();
    }
    return result;
  }

  private int read() throws IOException {
    if (myPos >= 0) {
      byte result = myBuffer[myPos];
      myPos--;
      return result;
    }
    return myInputStream.read();
  }

  private class ReadLine {
    private String myCurrentEOL = "";
    private ByteArrayOutputStream myResult = null;

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
            if (myCurrentEOL.equals("") || myCurrentEOL.equals("\r")) {
              myCurrentEOL += new String(new byte[]{(byte) ch});
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

  private byte[] readLineInternal() throws IOException {
    return new ReadLine().execute();
  }

  private void unread(int b) throws IOException {
    myPos++;
    if (myPos >= myBuffer.length) throw new IOException("Push back buffer is full");
    myBuffer[myPos] = (byte) b;

  }
}
