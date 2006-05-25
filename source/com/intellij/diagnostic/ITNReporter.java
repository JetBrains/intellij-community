package com.intellij.diagnostic;

import com.intellij.CommonBundle;
import com.intellij.errorreport.ErrorReportSender;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.NotifierBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NewBuildException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.errorreport.error.ThreadClosedException;
import com.intellij.ide.BrowserUtil;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NonNls;

import java.awt.Component;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 17, 2005
 * Time: 11:12:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class ITNReporter extends ErrorReportSubmitter {
  private static int previousExceptionThreadId = 0;
  private static boolean wasException = false;
  @NonNls private static final String URL_HEADER = "http://www.intellij.net/tracker/idea/viewSCR?publicId=";

  public String getReportActionText() {
    return DiagnosticBundle.message("error.report.to.jetbrains.action");
  }

  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent) {
    return sendError(events[0], parentComponent);
  }

  /**
     * @noinspection ThrowablePrintStackTrace
     */
  private static SubmittedReportInfo sendError(IdeaLoggingEvent event, Component parentComponent) {
    NotifierBean notifierBean = new NotifierBean();
    ErrorBean errorBean = new ErrorBean();
    errorBean.autoInit();
    errorBean.setLastAction(IdeaLogger.ourLastActionId);

    int threadId = 0;
    SubmittedReportInfo.SubmissionStatus submissionStatus = SubmittedReportInfo.SubmissionStatus.FAILED;

    do {
      // prepare
      try {
        ErrorReportSender sender = ErrorReportSender.getInstance();

        sender.prepareError(event.getThrowable());

        EAPSendErrorDialog dlg = new EAPSendErrorDialog();
        dlg.show();

        @NonNls String itnLogin = ErrorReportConfigurable.getInstance().ITN_LOGIN;
        @NonNls String itnPassword = ErrorReportConfigurable.getInstance().getPlainItnPassword();
        if (itnLogin.trim().length() == 0 && itnPassword.trim().length() == 0) {
          itnLogin = "idea_anonymous";
          itnPassword = "guest";
        }
        notifierBean.setItnLogin(itnLogin);
        notifierBean.setItnPassword(itnPassword);

        String description = dlg.getErrorDescription();
        String message = event.getMessage();

        @NonNls StringBuilder descBuilder = new StringBuilder();
        if (description.length() > 0) {
          descBuilder.append("User description: ").append(description).append("\n");
        }
        if (message != null) {
          descBuilder.append("Error message: ").append(message).append("\n");
        }
        if (previousExceptionThreadId != 0) {
          descBuilder.append("Previous exception is: ").append(URL_HEADER).append(previousExceptionThreadId).append("\n");
        }
        if (wasException) {
          descBuilder.append("There was at least one exception before this one.\n");
        }

        errorBean.setDescription(descBuilder.toString());

        if (dlg.isShouldSend()) {
          threadId = sender.sendError(notifierBean, errorBean);
          previousExceptionThreadId = threadId;
          wasException = true;
          submissionStatus = SubmittedReportInfo.SubmissionStatus.NEW_ISSUE;

          Messages.showInfoMessage(parentComponent,
                                   DiagnosticBundle.message("error.report.confirmation"),
                                   ReportMessages.ERROR_REPORT);
          break;
        }
        else {
          break;
        }

      }
      catch (NoSuchEAPUserException e) {
        if (Messages.showYesNoDialog(parentComponent, DiagnosticBundle.message("error.report.authentication.failed"),
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }
      catch (InternalEAPException e) {
        if (Messages.showYesNoDialog(parentComponent, DiagnosticBundle.message("error.report.posting.failed", e.getMessage()),
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }
      catch (IOException e) {
        if (!IOExceptionDialog.showErrorDialog(e, DiagnosticBundle.message("error.report.exception.title"),
                                               DiagnosticBundle.message("error.report.failure.message"))) {
          break;
        }
      }
      catch (NewBuildException e) {
        Messages.showMessageDialog(parentComponent,
                                   DiagnosticBundle.message("error.report.new.eap.build.message", e.getMessage()), CommonBundle.getWarningTitle(),
                                   Messages.getWarningIcon());
        break;
      }
      catch (ThreadClosedException e) {
        submissionStatus = SubmittedReportInfo.SubmissionStatus.DUPLICATE;
        threadId = e.getThreadId();
        if (Messages.showYesNoDialog(parentComponent,
                                     DiagnosticBundle.message("error.report.already.closed.message", e.getMessage()),
                                     ReportMessages.ERROR_REPORT, Messages.getQuestionIcon()) == 0) {
          try {
            BrowserUtil.launchBrowser(URL_HEADER + threadId);
          }
          catch (IllegalThreadStateException ex) {
            // it's OK
            // browser is not exited
          }
        }
        break;
      }
      catch (Exception e) {
        if (Messages.showYesNoDialog(parentComponent, DiagnosticBundle.message("error.report.sending.failure"),
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }

    }
    while (true);

    return new SubmittedReportInfo(submissionStatus != SubmittedReportInfo.SubmissionStatus.FAILED ? URL_HEADER + threadId : null,
                                   String.valueOf(threadId),
                                   submissionStatus);
  }
}
