package com.intellij.errorreport;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.ExceptionBean;
import com.intellij.errorreport.bean.NotifierBean;
import com.intellij.errorreport.error.*;
import com.intellij.errorreport.itn.ITNProxy;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.net.HttpConfigurable;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 22, 2003
 * Time: 8:57:19 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportSender {
  @NonNls public static final String PREPARE_URL = "http://www.intellij.net/";
  //public static String REPORT_URL = "http://unit-038:8080/error/report?sender=i";

  @NonNls public static final String PRODUCT_CODE = "idea";
  @NonNls private static final String PARAM_EMAIL = "EMAIL";
  @NonNls private static final String PARAM_EAP = "eap";

  public static ErrorReportSender getInstance () {
    return new ErrorReportSender();
  }

  protected ErrorReportSender () {
  }

  class SendTask {
    private Project myProject;
    private NotifierBean notifierBean;
    private ErrorBean errorBean;
    private Throwable throwable;
    private ExceptionBean exceptionBean;

    private ITask [] prepareTasks = null;
    private ITask [] doTasks = null;

    public SendTask(final Project project, Throwable throwable) {
      myProject = project;
      this.throwable = throwable;
    }

    public int getThreadId () {
      return exceptionBean.getItnThreadId();
    }

    public ITask [] getPrepareSteps () {
      if (prepareTasks == null)
        prepareTasks =  new ITask [] {
          new ITask () {
            private boolean checked = false;

            public boolean isSuccessful() {
              return checked;
            }

            public String getDescription() {
              return DiagnosticBundle.message("error.report.step.check.new.eap");
            }

            public void run() {
              try {
                UpdateChecker.NewVersion newVersion = UpdateChecker.checkForUpdates();
                if (newVersion != null) {
                  throw new NewBuildException(Integer.toString(newVersion.getLatestBuild()));
                }
                checked = true;
                exceptionBean = new ExceptionBean(throwable);
              }
              catch (NewBuildException e) {
                throw new SendException(e);
              }
              catch (ConnectionException e) {
                throw new SendException(e);
              }
            }
          }
        };
      return prepareTasks;
    }

    public ITask [] getDoSteps () {
      if (doTasks == null)
        doTasks = new ITask [] {
          new ITask () {
            private boolean authorized = false;

            public String getDescription() {
              return DiagnosticBundle.message("error.report.step.authorize");
            }

            public boolean isSuccessful() {
              return authorized;
            }

            public void run() {
              errorBean.setExceptionHashCode(exceptionBean.getHashCode());

              final Ref<Exception> err = new Ref<Exception>();
              Runnable runnable = new Runnable() {
                public void run() {
                  try {
                    HttpConfigurable.getInstance().prepareURL(PREPARE_URL);

                    if (notifierBean.getItnLogin() != null && notifierBean.getItnLogin().length() > 0) {
                      int threadId = ITNProxy.postNewThread(
                        notifierBean.getItnLogin(),
                        notifierBean.getItnPassword(),
                        errorBean, exceptionBean,
                        IdeaLogger.getOurCompilationTimestamp());
                      exceptionBean.setItnThreadId(threadId);

                      authorized = true;
                    }
                  }
                  catch (Exception ex) {
                    err.set(ex);
                  }
                }
              };
              if (myProject == null) {
                runnable.run();
              }
              else {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable,
                                                                                  DiagnosticBundle.message("title.submitting.error.report"),
                                                                                  false, myProject);
              }
              if (!err.isNull()) {
                throw new SendException(err.get());
              }
            }
          }
        };
      return doTasks;
    }

    public void setErrorBean(ErrorBean error) {
      errorBean = error;
    }

    public void setNotifierBean(NotifierBean notifierBean) {
      this.notifierBean = notifierBean;
    }
  }

  private SendTask sendTask;

  public void prepareError(Project project, Throwable exception)
    throws IOException, XmlRpcException, NewBuildException, ThreadClosedException {

    sendTask = new SendTask (project, exception);
    TaskRunner prepareRunner = new TaskRunner(sendTask.getPrepareSteps());

    try {
      prepareRunner.doTasks();
    } catch (IOException e) {
      throw e;
    } catch (XmlRpcException e) {
      throw e;
    } catch (NewBuildException e) {
      throw e;
    } catch (ThreadClosedException e) {
      throw e;
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public int sendError (NotifierBean notifierBean, ErrorBean error)
    throws XmlRpcException, IOException, NoSuchEAPUserException, InternalEAPException {

    sendTask.setErrorBean (error);
    sendTask.setNotifierBean (notifierBean);
    TaskRunner taskRunner = new TaskRunner(sendTask.getDoSteps());

    try {
      taskRunner.doTasks();
      return sendTask.getThreadId();
    } catch (IOException e) {
      throw e;
    } catch (XmlRpcException e) {
      throw e;
    } catch (NoSuchEAPUserException e) {
      throw e;
    } catch (InternalEAPException e) {
      throw e;
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
