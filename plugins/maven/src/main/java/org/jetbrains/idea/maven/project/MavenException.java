package org.jetbrains.idea.maven.project;

import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.validation.ModelValidationResult;

import java.util.Collections;
import java.util.List;

public class MavenException extends Exception {
  private String myDetails;

  public MavenException(String message) {
    this(new Exception(message));
  }

  public MavenException(Exception e) {
    this(Collections.singletonList(e));
  }

  public MavenException(List<Exception> ee) {
    myDetails = collectDetails(ee);
  }

  private String collectDetails(List<Exception> ee) {
    String result = "";
    for (Exception e : ee) {
      for (String s : collectDetails(e)) {
        result += result.length() == 0 ? "" : "\n";
        result += s;
      }
    }
    return result;
  }

  private List<String> collectDetails(Exception e) {
    if (e instanceof InvalidProjectModelException) {
      ModelValidationResult r = ((InvalidProjectModelException)e).getValidationResult();
      return r.getMessages();
    }

    String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
    return Collections.singletonList(m);
  }

  @Override
  public String getMessage() {
    return myDetails;
  }
}
