package com.intellij.tasks.generic;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * ResponseHandler subclasses represent different strategies of extracting tasks from
 * task server responses (e.g. using regular expressions, XPath, JSONPath, CSS selector etc.)
 *
 * @see XPathResponseHandler
 * @see JsonPathResponseHandler
 * @see RegExResponseHandler
 * @author Mikhail Golubev
 */
public abstract class ResponseHandler implements Cloneable {

  protected GenericRepository myRepository;

  /**
   * Serialization constructor
   */
  public ResponseHandler() {
    // empty
  }

  public ResponseHandler(@NotNull GenericRepository repository) {
    myRepository = repository;
  }

  public void setRepository(@NotNull GenericRepository repository) {
    myRepository = repository;
  }

  @NotNull
  @Transient
  public GenericRepository getRepository() {
    return myRepository;
  }

  @NotNull
  public abstract JComponent getConfigurationComponent(@NotNull Project project);

  @NotNull
  public abstract ResponseType getResponseType();

  @NotNull
  public abstract Task[] parseIssues(@NotNull String response, int max) throws Exception;

  @Nullable
  public abstract Task parseIssue(@NotNull String response) throws Exception;

  public abstract boolean isConfigured();

  @Override
  public ResponseHandler clone() {
    try {
      return (ResponseHandler) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("ResponseHandler#clone() should be supported");
    }
  }
}
