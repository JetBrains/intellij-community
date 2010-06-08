/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */

package org.jetbrains.idea.maven.facade.embedder;

import org.codehaus.plexus.logging.Logger;
import org.jetbrains.idea.maven.facade.MavenFacadeConsole;

import java.rmi.RemoteException;

public class MavenFacadeConsoleWrapper implements Logger {
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private MavenFacadeConsole myWrappee;
  private int myThreshold;

  void doPrint(int level, String message, Throwable throwable) {
    if (level < myThreshold) return;

    if (!message.endsWith(LINE_SEPARATOR)) {
      message += LINE_SEPARATOR;
    }

    if (myWrappee != null) {
      try {
        myWrappee.printMessage(level, message, throwable);
      }
      catch (RemoteException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void setWrappee(MavenFacadeConsole wrappee) {
    myWrappee = wrappee;
  }

  public void debug(String string, Throwable throwable) {
    doPrint(MavenFacadeConsole.LEVEL_DEBUG, string, throwable);
  }

  public void info(String string, Throwable throwable) {
    doPrint(MavenFacadeConsole.LEVEL_INFO, string, throwable);
  }

  public void warn(String string, Throwable throwable) {
    doPrint(MavenFacadeConsole.LEVEL_WARN, string, throwable);
  }

  public void error(String string, Throwable throwable) {
    doPrint(MavenFacadeConsole.LEVEL_ERROR, string, throwable);
  }

  public void fatalError(String string, Throwable throwable) {
    doPrint(MavenFacadeConsole.LEVEL_FATAL, string, throwable);
  }

  public void debug(String message) {
    debug(message, null);
  }

  public boolean isDebugEnabled() {
    return getThreshold() <= MavenFacadeConsole.LEVEL_DEBUG;
  }

  public void info(String message) {
    info(message, null);
  }

  public boolean isInfoEnabled() {
    return getThreshold() <= MavenFacadeConsole.LEVEL_INFO;
  }

  public void warn(String message) {
    warn(message, null);
  }

  public boolean isWarnEnabled() {
    return getThreshold() <= MavenFacadeConsole.LEVEL_WARN;
  }

  public void error(String message) {
    error(message, null);
  }

  public boolean isErrorEnabled() {
    return getThreshold() <= MavenFacadeConsole.LEVEL_ERROR;
  }

  public void fatalError(String message) {
    fatalError(message, null);
  }

  public boolean isFatalErrorEnabled() {
    return getThreshold() <= MavenFacadeConsole.LEVEL_FATAL;
  }

  public void setThreshold(int threshold) {
    this.myThreshold = threshold;
  }

  public int getThreshold() {
    return myThreshold;
  }

  public Logger getChildLogger(String s) {
    return null;
  }

  public String getName() {
    return toString();
  }
}
