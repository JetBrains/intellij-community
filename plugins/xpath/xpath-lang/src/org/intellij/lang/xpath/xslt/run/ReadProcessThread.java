/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt.run;

import com.intellij.util.Alarm;

import java.io.Reader;
import java.io.IOException;

/* Copied from com.intellij.execution.process.OSProcessHandler.ReadProcessThread */
@SuppressWarnings({"ALL"})
abstract class ReadProcessThread extends Thread {
  private static final int NOTIFY_TEXT_DELAY = 300;

  private final Reader myReader;

  private final StringBuffer myBuffer = new StringBuffer();
  private final Alarm myAlarm;

  private boolean myIsClosed = false;

  public ReadProcessThread(final Reader reader) {
    //noinspection HardCodedStringLiteral
    super("ReadProcessThread "+reader.getClass().getName());
    setPriority(Thread.MAX_PRIORITY);
    myReader = reader;
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  }

  public void run() {
    myAlarm.addRequest(new Runnable() {
      public void run() {
        if(!isClosed()) {
          myAlarm.addRequest(this, NOTIFY_TEXT_DELAY);
          checkTextAvailable();
        }
      }
    }, NOTIFY_TEXT_DELAY);

    try {
      while (!isClosed()) {
        final int c = readNextByte();
        if (c == -1) {
          break;
        }
        synchronized (myBuffer) {
          myBuffer.append((char)c);
        }
        if (c == '\n') { // not by '\r' because of possible '\n'
          checkTextAvailable();
        }
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    close();
  }

  private int readNextByte() {
    try {
      return myReader.read();
    }
    catch (IOException e) {
      return -1; // When process terminated Process.getInputStream()'s underlaying stream becomes closed on Linux.
    }
  }

  private void checkTextAvailable() {
    synchronized (myBuffer) {
      if (myBuffer.length() == 0) return;
      // warning! Since myBuffer is reused, do not use myBuffer.toString() to fetch the string
      // because the created string will get StringBuffer's internal char array as a buffer which is possibly too large.
      final String s = myBuffer.substring(0, myBuffer.length());
      myBuffer.setLength(0);
      textAvailable(s);
    }
  }

  public void close() {
    synchronized (this) {
      if (isClosed()) {
        return;
      }
      myIsClosed = true;
    }
    try {
      if(Thread.currentThread() != this) {
        join(0);
      }
    }
    catch (InterruptedException e) {
    }
    // must close after the thread finished its execution, cause otherwise
    // the thread will try to read from the closed (and nulled) stream
    try {
      myReader.close();
    }
    catch (IOException e1) {
      // supressed
    }
    checkTextAvailable();
  }

  protected abstract void textAvailable(final String s);

  private synchronized boolean isClosed() {
    return myIsClosed;
  }

}
