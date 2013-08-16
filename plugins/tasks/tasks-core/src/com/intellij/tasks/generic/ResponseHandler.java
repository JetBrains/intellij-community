package com.intellij.tasks.generic;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * ResponseHandler subclasses represent different strategies of extracting tasks from
 * task server responses (e.g. using regular expressions, XPath, JSONPath, CSS selector etc.).
 *
 * @see XPathResponseHandler
 * @see JsonPathResponseHandler
 * @see RegExResponseHandler
 * @author Mikhail Golubev
 */
public abstract class ResponseHandler implements Cloneable {

  // XXX: what about serialization of circular dependencies?
  protected GenericRepository myRepository;

  // Serialization constructor
  public ResponseHandler() {
    // empty
  }

  public ResponseHandler(GenericRepository repository) {
    myRepository = repository;
  }

  public void setRepository(GenericRepository repository) {
    myRepository = repository;
  }

  @Transient
  public GenericRepository getRepository() {
    return myRepository;
  }

  public abstract JComponent getConfigurationComponent(Project project);

  public abstract ResponseType getResponseType();

  @NotNull
  public abstract Task[] parseIssues(String response) throws Exception;

  @Nullable
  public abstract Task parseIssue(String response) throws Exception;

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
