package org.jetbrains.idea.maven.project;

import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.validation.ModelValidationResult;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenException extends Exception {
  private String mySummary;
  private List<Error> myErrors = new ArrayList<Error>();

  public MavenException(String message) {
    this(new Exception(message));
  }

  public MavenException(Exception e) {
    this(Collections.singletonList(e));
  }

  public MavenException(List<Exception> ee) {
    mySummary = "";

    for (Exception exception : ee) {
      Error error = createError(exception);
      myErrors.add(error);

      for (String s : error.messages) {
        mySummary += mySummary.length() == 0 ? "" : "\n";
        mySummary += s;
      }
    }
  }

  private Error createError(Exception e) {
    Error result = new Error();

    result.pomFile = retrievePomFilePath(e);
    result.messages = collectMessages(e);

    return result;
  }

  private String retrievePomFilePath(Exception e) {
    try {
      Method m = e.getClass().getMethod("getPomFile");
      File f = (File)m.invoke(e);
      return f == null ? null : f.getPath();
    }
    catch (NoSuchMethodException ex) {
    }
    catch (InvocationTargetException ex) {
    }
    catch (IllegalAccessException ex) {
    }
    return null;
  }

  private List<String> collectMessages(Exception e) {
    if (e instanceof InvalidProjectModelException) {
      ModelValidationResult r = ((InvalidProjectModelException)e).getValidationResult();
      return r.getMessages();
    } else {
      String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
      return Collections.singletonList(m);
    }
  }

  @Override
  public String getMessage() {
    return mySummary;
  }

  public List<Error> getErrors() {
    return myErrors;
  }

  public static class Error {
    public String pomFile;
    public List<String> messages;
  }
}
