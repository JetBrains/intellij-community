package com.intellij.errorreport.bean;

import com.intellij.errorreport.Util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 19, 2003
 * Time: 9:07:26 PM
 * To change this template use Options | File Templates.
 */
public class ExceptionBean {
  private String hashCode;
  private String message;
  private Date date;
  private String stackTrace;
  private int itnThreadId;
  private String buildNumber;
  private String productCode;
  private boolean scrambled;

  public static final int NO_EAP_THREAD = -1;

  private String exceptionClass = "";

  public String getExceptionClass() { return exceptionClass; }

  protected ExceptionBean (ExceptionBean e) {
    hashCode = e.hashCode;
    message = e.message;
    date = e.date;
    stackTrace = e.stackTrace;
    itnThreadId = e.itnThreadId;
    buildNumber = e.buildNumber;
    productCode = e.productCode;
    scrambled = e.scrambled;
  }

  public ExceptionBean() {
  }

  public ExceptionBean (Throwable throwable) {
    if (throwable != null) {
      exceptionClass = throwable.getClass().getName();

      message = throwable.getMessage();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      throwable.printStackTrace(new PrintStream (baos, true));
      stackTrace = baos.toString();

      try {
        hashCode = Util.md5(stackTrace, "stack-trace");
      } catch (NoSuchAlgorithmException e) {
        hashCode = null;
      }
    }
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  public String getHashCode() {
    return hashCode;
  }

  public void setHashCode(String hashCode) {
    this.hashCode = hashCode;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public String getBuildNumber() {
    return buildNumber;
  }

  public void setBuildNumber(String buildNumber) {
    this.buildNumber = buildNumber;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public int getItnThreadId() {
    return itnThreadId;
  }

  public void setItnThreadId(int itnThreadId) {
    this.itnThreadId = itnThreadId;
  }

  public boolean isScrambled() {
    return scrambled;
  }

  public void setScrambled(boolean scrambled) {
    this.scrambled = scrambled;
  }
}
