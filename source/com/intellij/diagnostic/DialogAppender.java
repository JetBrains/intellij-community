package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Mike
 */
public class DialogAppender extends AppenderSkeleton {
  private static final DefaultIdeaErrorLogger DEFAULT_LOGGER = new DefaultIdeaErrorLogger();
  static final boolean RELEASE_BUILD = false;

  private Thread myDialogThread = null;

  protected synchronized void append(final LoggingEvent event) {
    List<ErrorLogger> loggers = new ArrayList<ErrorLogger>();
    loggers.add(DEFAULT_LOGGER);

    Application application = ApplicationManager.getApplication();
    if (application != null) {
      if (application.isHeadlessEnvironment() || application.isDisposed()) return;
      loggers.addAll(Arrays.asList(application.getComponents(ErrorLogger.class)));
    }

    appendToLoggers(event, loggers.toArray(new ErrorLogger[loggers.size()]));
  }

  void appendToLoggers(final LoggingEvent event, ErrorLogger[] errorLoggers) {

    if (event.level.isGreaterOrEqual(Priority.WARN)) {
      if (myDialogThread != null) {
        return;
      }
      ThrowableInformation throwable = event.getThrowableInformation();
      if (throwable == null) {
        return;
      }

      final IdeaLoggingEvent ideaEvent = new IdeaLoggingEvent((String)event.getMessage(), throwable.getThrowable());
      for (int i = errorLoggers.length - 1; i >= 0; i--) {

        final ErrorLogger logger = errorLoggers[i];
        if (logger.canHandle(ideaEvent)) {

          //noinspection HardCodedStringLiteral
          myDialogThread = new Thread(new Runnable() {
            public void run() {
              try {
                logger.handle(ideaEvent);
              }
              finally {
                myDialogThread = null;
              }
            }
          }, "DialogAppender");
          myDialogThread.start();

          break;
        }
      }
    }
  }

  public boolean requiresLayout() {
    return false;
  }

  public void close() {
  }
}

