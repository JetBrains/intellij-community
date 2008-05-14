package org.jetbrains.idea.maven.project;

import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.validation.ModelValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenException extends Exception {
  private String myPomPath;
  private String mySummary;
  private List<String> myMessages = new ArrayList<String>();

  public MavenException(String message) {
    this(new Exception(message));
  }

  public MavenException(Exception e) {
    this(e, null);
  }

  public MavenException(Exception e, String pomPath) {
    this(Collections.singletonList(e), pomPath);
  }

  public MavenException(List<Exception> ee) {
    this(ee, null);
  }

  public MavenException(List<Exception> ee, String pomPath) {
    super(ee.get(0));
    myPomPath = pomPath;

    for (Exception exception : ee) {
      myMessages.addAll(collectMessages(exception));
    }

    mySummary = "Problems in " + pomPath + ":";
    for (String s : myMessages) {
      mySummary += mySummary.length() == 0 ? "" : "\n";
      mySummary += s;
    }
  }

  private List<String> collectMessages(Exception e) {
    if (e instanceof InvalidProjectModelException) {
      ModelValidationResult r = ((InvalidProjectModelException)e).getValidationResult();
      if (r != null) return r.getMessages();
    }

    String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
    return Collections.singletonList(m);
  }

  public String getPomPath() {
    return myPomPath;
  }

  @Override
  public String getMessage() {
    return mySummary;
  }

  public List<String> getMessages() {
    return myMessages;
  }
}
