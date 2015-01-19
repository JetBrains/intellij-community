/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.idea.svn.NativeLogReader;
import org.jetbrains.idea.svn.SSLExceptionsHelper;
import org.jetbrains.idea.svn.SvnNativeLogParser;
import org.jetbrains.idea.svn.networking.SSLProtocolExceptionParser;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitDebugLogger extends SVNDebugLogAdapter {
  private final boolean myLoggingEnabled;
  private final boolean myLogNative;
  private final Logger myLog;
  private final static long ourErrorNotificationInterval = TimeUnit.MINUTES.toMillis(2);
  private long myPreviousTime = 0;

  public SvnKitDebugLogger(boolean loggingEnabled, boolean logNative, Logger log) {
    myLoggingEnabled = loggingEnabled;
    myLogNative = logNative;
    myLog = log;
  }

  private boolean shouldLog(final SVNLogType logType) {
    return myLoggingEnabled || myLogNative && SVNLogType.NATIVE_CALL.equals(logType);
  }

  @Override
  public void log(final SVNLogType logType, final Throwable th, final Level logLevel) {
    handleSpecificSSLExceptions(th);
    if (shouldLog(logType)) {
      myLog.info(th);
    }
  }

  private void handleSpecificSSLExceptions(Throwable th) {
    final long time = System.currentTimeMillis();
    if ((time - myPreviousTime) <= ourErrorNotificationInterval) {
      return;
    }
    if (th instanceof SSLHandshakeException) {
      // not trusted certificate exception is not the problem, just part of normal behaviour
      if (th.getCause() instanceof SVNSSLUtil.CertificateNotTrustedException) {
        myLog.info(th);
        return;
      }

      myPreviousTime = time;
      String info = SSLExceptionsHelper.getAddInfo();
      info = info == null ? "" : " (" + info + ") ";
      if (th.getCause() instanceof CertificateException) {
        PopupUtil.showBalloonForActiveFrame("Subversion: " + info + th.getCause().getMessage(), MessageType.ERROR);
      }
      else {
        final String postMessage = "\nPlease check Subversion SSL settings (Settings | Version Control | Subversion | Network)\n" +
                                   "Maybe you should specify SSL protocol manually - SSLv3 or TLSv1";
        PopupUtil.showBalloonForActiveFrame("Subversion: " + info + th.getMessage() + postMessage, MessageType.ERROR);
      }
    }
    else if (th instanceof SSLProtocolException) {
      final String message = th.getMessage();
      if (!StringUtil.isEmptyOrSpaces(message)) {
        myPreviousTime = time;
        String info = SSLExceptionsHelper.getAddInfo();
        info = info == null ? "" : " (" + info + ") ";
        final SSLProtocolExceptionParser parser = new SSLProtocolExceptionParser(message);
        parser.parse();
        final String errMessage = "Subversion: " + info + parser.getParsedMessage();
        PopupUtil.showBalloonForActiveFrame(errMessage, MessageType.ERROR);
      }
    }
  }

  @Override
  public void log(final SVNLogType logType, final String message, final Level logLevel) {
    if (SVNLogType.NATIVE_CALL.equals(logType)) {
      logNative(message);
    }
    if (shouldLog(logType)) {
      myLog.info(message);
    }
  }

  private static void logNative(String message) {
    if (message == null) return;
    final NativeLogReader.CallInfo callInfo = SvnNativeLogParser.parse(message);
    if (callInfo == null) return;
    NativeLogReader.putInfo(callInfo);
  }

  @Override
  public void log(final SVNLogType logType, final String message, final byte[] data) {
    if (shouldLog(logType)) {
      if (data != null) {
        myLog.info(message + "\n" + new String(data, CharsetToolkit.UTF8_CHARSET));
      }
      else {
        myLog.info(message);
      }
    }
  }
}
