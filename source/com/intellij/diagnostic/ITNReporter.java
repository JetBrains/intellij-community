package com.intellij.diagnostic;

import com.intellij.errorreport.ErrorReportSender;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.NotifierBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NewBuildException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.errorreport.error.ThreadClosedException;
import com.intellij.errorreport.itn.ITNProxy;
import com.intellij.ide.BrowserUtil;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.net.IOExceptionDialog;

import java.awt.*;
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
  private static final String URL_HEADER = "http://www.intellij.net/tracker/idea/viewSCR?publicId=";

  public String getReportActionText() {
    return "Report to JetBrains";
  }

  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent) {
    return sendError(events[0], parentComponent);
  }

  /**
     * @noinspection ThrowablePrintStackTrace
     */
  private SubmittedReportInfo sendError(IdeaLoggingEvent event, Component parentComponent) {
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

        boolean anonymousLogin = false;
        String itnLogin = ErrorReportConfigurable.getInstance().ITN_LOGIN;
        String itnPassword = ErrorReportConfigurable.getInstance().getPlainItnPassword();
        if (itnLogin.trim().length() == 0 && itnPassword.trim().length() == 0) {
          anonymousLogin = true;
          itnLogin = "idea_anonymous";
          itnPassword = "guest";
        }
        notifierBean.setItnLogin(itnLogin);
        notifierBean.setItnPassword(itnPassword);

        String description = dlg.getErrorDescription();
        String message = event.getMessage();

        errorBean.setDescription((description.length() > 0 ? "User description: " + description + "\n" : "")+
                                 (message != null ? "Error message: " + message + "\n" : ""));

        if (dlg.isShouldSend()) {
          threadId = sender.sendError(notifierBean, errorBean);

          if (previousExceptionThreadId != 0) {
            ITNProxy.postNewComment(ErrorReportConfigurable.getInstance().ITN_LOGIN,
                                    ErrorReportConfigurable.getInstance().getPlainItnPassword(),
                                    threadId, "Previous exception is: " +
                                              URL_HEADER +
                                              previousExceptionThreadId);
          }
          previousExceptionThreadId = threadId;

          if (wasException) {
            ITNProxy.postNewComment(ErrorReportConfigurable.getInstance().ITN_LOGIN,
                                    ErrorReportConfigurable.getInstance().getPlainItnPassword(),
                                    threadId, "There was at least one exception before this one.");
          }

          wasException = true;
          submissionStatus = SubmittedReportInfo.SubmissionStatus.NEW_ISSUE;

          if (anonymousLogin) {
            Messages.showInfoMessage(parentComponent,
                                     "Error report successfully sent. Thank you for your feedback!",
                                     ReportMessages.ERROR_REPORT);
          }
          else {
            if (Messages.showYesNoDialog(parentComponent,
                                         "Error report successfully sent. \nRequest #" + threadId +
                                         " created. Do you want to open the related request in ITN?",
                                         ReportMessages.ERROR_REPORT, Messages.getQuestionIcon()) == 0) {
              try {
                BrowserUtil.launchBrowser(URL_HEADER + threadId);
              }
              catch (IllegalThreadStateException e) {
                // it's OK
                // browser is not exited
              }
            }
          }
          break;
        }
        else {
          break;
        }

      }
      catch (NoSuchEAPUserException e) {
        if (ReportMessages.isEAP) e.printStackTrace();
        if (Messages.showYesNoDialog(parentComponent, "ITN authorization failed. Do you want to try again?",
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }
      catch (InternalEAPException e) {
        if (ReportMessages.isEAP) e.printStackTrace();
        if (Messages.showYesNoDialog(parentComponent, "ITN posting failed. Do you want to try again?",
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }
      catch (IOException e) {
        if (ReportMessages.isEAP) e.printStackTrace();
        if (!IOExceptionDialog.showErrorDialog(e, "Error Report", "Error report sending failed.")) {
          break;
        }
      }
      catch (NewBuildException e) {
        if (ReportMessages.isEAP) e.printStackTrace();
        Messages.showMessageDialog(parentComponent,
                                   "New EAP build " + e.getMessage() + " is available.", "Warning",
                                   Messages.getWarningIcon());
        break;
      }
      catch (ThreadClosedException e) {
        submissionStatus = SubmittedReportInfo.SubmissionStatus.DUPLICATE;
        threadId = e.getThreadId();
        if (ReportMessages.isEAP) e.printStackTrace();
        if (Messages.showYesNoDialog(parentComponent,
                                     "This error already closed with status: " + e.getMessage() + "." +
                                     " You don't need to post it, thank you. Do you want to open it in tracker?",
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
        if (ReportMessages.isEAP) e.printStackTrace();
        if (Messages.showYesNoDialog(parentComponent, "Sending failed. Do you want to try again?",
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
