package com.intellij.errorreport;

import com.intellij.diagnostic.ErrorReportConfigurable;
import com.intellij.errorreport.bean.BeanWrapper;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.ExceptionBean;
import com.intellij.errorreport.bean.NotifierBean;
import com.intellij.errorreport.error.*;
import com.intellij.errorreport.itn.ITNProxy;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.net.HttpConfigurable;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;

import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 22, 2003
 * Time: 8:57:19 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportSender {
  public static final String PREPARE_URL = "http://www.intellij.net/websupport/error/";
  public static String REPORT_URL = "http://www.intellij.net/websupport/error/report?sender=i";
  //public static String REPORT_URL = "http://unit-038:8080/error/report?sender=i";

  public static final String PRODUCT_CODE = "idea";

  public static ErrorReportSender getInstance () {
    return new ErrorReportSender();
  }

  protected ErrorReportSender () {
  }

  protected static String authorizeEmail (String email) throws IOException, XmlRpcException {
    XmlRpcClient client = new XmlRpcClient(REPORT_URL);
    Vector params = new Vector ();
    params.add("EMAIL");
    params.add(email);

    String notifierId = (String) client.execute(RemoteMethods.ERROR_AUTHORIZE, params);
    return notifierId;
  }

  protected static String authorizeEAP (String eapLogin) throws IOException, XmlRpcException {
    XmlRpcClient client = new XmlRpcClient(REPORT_URL);
    Vector params = new Vector ();
    params.add("eap");
    params.add(eapLogin);

    String notifierId = (String) client.execute(RemoteMethods.ERROR_AUTHORIZE, params);
    return notifierId;
  }

  protected static ExceptionBean checkException (Throwable e) throws IOException, XmlRpcException, NoSuchExceptionException {
    if (e instanceof StackOverflowError) {
      throw new NoSuchExceptionException(e.getMessage());
    }

    XmlRpcClient client = new XmlRpcClient(REPORT_URL);
    Vector params = new Vector ();
    ExceptionBean exceptionBean = new ExceptionBean(e);
    params.add(exceptionBean.getHashCode());

    try {
      Hashtable hashtable = (Hashtable) client.execute(RemoteMethods.ERROR_CHECK_EXCEPTION, params);
      return BeanWrapper.getException(hashtable);
    } catch (XmlRpcException ex) {
      if (NoSuchExceptionException.isException(ex))
        throw new NoSuchExceptionException(ex.getMessage());
      else
        throw ex;
    }
  }

  protected static void postError (ErrorBean error, ExceptionBean exceptionBean)
    throws IOException, XmlRpcException {
    XmlRpcClient client = new XmlRpcClient(REPORT_URL);
    Vector params = new Vector ();
    params.add(BeanWrapper.getHashtable(error));
    params.add(BeanWrapper.getHashtable(exceptionBean));

    client.execute(RemoteMethods.ERROR_POST_ERROR, params);
  }

  class SendTask {
    private NotifierBean notifierBean;
    private ErrorBean errorBean;
    private Throwable throwable;
    private ExceptionBean exceptionBean;
    private boolean newThread = false;
    private int buildNumber = -1;

    private ITask [] prepareTasks = null;
    private ITask [] doTasks = null;

    public SendTask(Throwable throwable) {
      this.throwable = throwable;
    }

    public SendTask(ErrorBean errorBean, Throwable throwable, NotifierBean notifierBean) {
      this.errorBean = errorBean;
      this.throwable = throwable;
      this.notifierBean = notifierBean;
    }

    public int getThreadId () {
      return exceptionBean.getItnThreadId();
    }

    public ITask [] getPrepareSteps () {
      if (prepareTasks == null)
        prepareTasks =  new ITask [] {
          new ITask () {
            private boolean cheked = false;

            public boolean isSuccessful() {
              return cheked;
            }

            public String getDescription() {
              return "Check for a new EAP build... ";
            }

            public void run() {
              try {
                int itnBuildNumber = ITNProxy.getBuildNumber();
                ApplicationInfoEx appInfo =
                  (ApplicationInfoEx) ApplicationManager.getApplication().getComponent(
                    ApplicationInfo.class);
                String strNumber = appInfo.getBuildNumber();
                buildNumber = -1;
                try {
                  buildNumber = Integer.valueOf(strNumber).intValue();
                } catch (NumberFormatException e) {

                }

                if (itnBuildNumber > 0 &&
                    buildNumber > 0 &&
                    itnBuildNumber > buildNumber) {
                  throw new NewBuildException (Integer.toString(itnBuildNumber));
                }

                cheked = true;
              }
              catch (IOException e) {
                throw new SendException(e);
              }
              catch (NewBuildException e) {
                throw new SendException(e);
              }
            }
          },
          new ITask () {
            private boolean checked = false;

            public String getDescription() {
              return "Checking exception... ";
            }

            public boolean isSuccessful() {
              return checked;
            }

            public void run() {
              try {
                exceptionBean = ErrorReportSender.checkException(throwable);
                newThread = false;
                checked = true;
              } catch (NoSuchExceptionException e) {
                exceptionBean = new ExceptionBean(throwable);
                newThread = true;
                checked = true;
              } catch (Exception e) {
                throw new SendException(e);
              }
            }
          },

          new ITask () {
            private boolean checked = false;

            public String getDescription() {
              return "Checking thread status...";
            }

            public boolean isSuccessful() {
              return checked;
            }

            public void run() {
              if (! newThread) {
                try {
                  String threadStatus = ITNProxy.getThreadStatus(exceptionBean.getItnThreadId());
                  checked = true;

                  if (! threadStatus.equals("Open") &&
                      ! threadStatus.equals("Submitted") &&
                      ! threadStatus.equals("More info needed")) {
                    ErrorReportConfigurable.getInstance().addClosed(exceptionBean.getHashCode(),
                                                                    Integer.toString(exceptionBean.getItnThreadId()));
                    throw new ThreadClosedException (threadStatus, exceptionBean.getItnThreadId());
                  }
                }
                catch (IOException e) {
                  throw new SendException(e);
                }
                catch (ThreadClosedException e) {
                  throw new SendException(e);
                }
              }
            }
          },
        };
      return prepareTasks;
    }

    public ITask [] getDoSteps () {
      if (doTasks == null)
        doTasks = new ITask [] {
          new ITask () {
            private boolean authorized = false;

            public String getDescription() {
              return "Authorizing... ";
            }

            public boolean isSuccessful() {
              return authorized;
            }

            public void run() {
              try {
                errorBean.setExceptionHashCode(exceptionBean.getHashCode());

                String notifierId = null;

                if (notifierBean.getEmail() != null && notifierBean.getEmail().length() > 0) {
                  notifierId = ErrorReportSender.authorizeEmail(notifierBean.getEmail());

                  authorized = true;
                } else if (notifierBean.getItnLogin() != null &&
                           notifierBean.getItnLogin().length() > 0) {
                  if (newThread) {
                    int threadId = ITNProxy.postNewThread(
                      notifierBean.getItnLogin(),
                      notifierBean.getItnPassword(),
                      errorBean, exceptionBean,
                      IdeaLogger.getOurCompilationTimestamp());
                    exceptionBean.setItnThreadId(threadId);
                  } else {
                    ITNProxy.postNewComment(notifierBean.getItnLogin(),
                                            notifierBean.getItnPassword(),
                                            exceptionBean.getItnThreadId(),
                                            errorBean.getDescription());
                  }
                  notifierId = ErrorReportSender.authorizeEAP(notifierBean.getItnLogin());

                  authorized = true;
                }
                errorBean.setNotifierId(notifierId);
              } catch (Exception e) {
                throw new SendException(e);
              }
            }
          },
          new ITask () {
            private boolean sent = false;

            public boolean isSuccessful() {
              return sent;
            }

            public String getDescription() {
              return "Sending... ";
            }

            public void run() {
              exceptionBean.setBuildNumber(Integer.toString(buildNumber));
              exceptionBean.setProductCode(PRODUCT_CODE);
              exceptionBean.setScrambled(buildNumber > 0);
              exceptionBean.setDate(new Date ());

              try {
                ErrorReportSender.postError(errorBean, exceptionBean);
                sent = true;
              } catch (Exception e) {
                throw new SendException(e);
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

  public void prepareError (Throwable exception)
    throws IOException, XmlRpcException, NewBuildException, ThreadClosedException {
    HttpConfigurable.getInstance().prepareURL(PREPARE_URL);

    sendTask = new SendTask (exception);
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
