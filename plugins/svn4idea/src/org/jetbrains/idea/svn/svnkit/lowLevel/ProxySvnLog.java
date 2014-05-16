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

import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/30/12
 * Time: 5:41 PM
 */
public class ProxySvnLog implements ISVNDebugLog {
  private final ISVNDebugLog myLog;

  public ProxySvnLog(ISVNDebugLog log) {
    myLog = log;
  }

  @Override
  public OutputStream createInputLogStream() {
    return myLog.createInputLogStream();
  }

  @Override
  public void flushStream(Object stream) {
    myLog.flushStream(stream);
  }

  @Override
  public OutputStream createOutputLogStream() {
    return myLog.createOutputLogStream();
  }

  @Override
  public OutputStream createLogStream(SVNLogType logType, OutputStream os) {
    return new SVNStoppableOutputStream(myLog.createLogStream(logType, os));
  }

  @Override
  public InputStream createLogStream(SVNLogType logType, InputStream is) {
    return new SVNStoppableInputStream(is, myLog.createLogStream(logType, is));
  }

  @Override
  public void logError(SVNLogType logType, String message) {
    myLog.logError(logType, message);
  }

  @Override
  public void logError(SVNLogType logType, Throwable th) {
    myLog.logError(logType, th);
  }

  @Override
  public void logSevere(SVNLogType logType, String message) {
    myLog.logSevere(logType, message);
  }

  @Override
  public void logSevere(SVNLogType logType, Throwable th) {
    myLog.logSevere(logType, th);
  }

  @Override
  public void logFine(SVNLogType logType, Throwable th) {
    myLog.logFine(logType, th);
  }

  @Override
  public void logFine(SVNLogType logType, String message) {
    myLog.logFine(logType, message);
  }

  @Override
  public void logFiner(SVNLogType logType, Throwable th) {
    myLog.logFiner(logType, th);
  }

  @Override
  public void logFiner(SVNLogType logType, String message) {
    myLog.logFiner(logType, message);
  }

  @Override
  public void logFinest(SVNLogType logType, Throwable th) {
    myLog.logFinest(logType, th);
  }

  @Override
  public void logFinest(SVNLogType logType, String message) {
    myLog.logFinest(logType, message);
  }

  @Override
  public void log(SVNLogType logType, Throwable th, Level logLevel) {
    myLog.log(logType, th, logLevel);
  }

  @Override
  public void log(SVNLogType logType, String message, Level logLevel) {
    myLog.log(logType, message, logLevel);
  }

  @Override
  public void log(SVNLogType logType, String message, byte[] data) {
    myLog.log(logType, message, data);
  }
}
