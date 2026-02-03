// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.generic;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * ResponseHandler subclasses represent different strategies of extracting tasks from
 * task server responses (e.g., using regular expressions, XPath, JSONPath, CSS selector, etc.)
 *
 * @see XPathResponseHandler
 * @see JsonPathResponseHandler
 * @see RegExResponseHandler
 * @author Mikhail Golubev
 */
@Transient
public abstract class ResponseHandler implements Cloneable {
  protected GenericRepository repository;

  /**
   * Serialization constructor
   */
  public ResponseHandler() {
    // empty
  }

  public ResponseHandler(@NotNull GenericRepository repository) {
    this.repository = repository;
  }

  public void setRepository(@NotNull GenericRepository repository) {
    this.repository = repository;
  }

  public @NotNull GenericRepository getRepository() {
    return repository;
  }

  public abstract @NotNull JComponent getConfigurationComponent(@NotNull Project project);

  public abstract @NotNull ResponseType getResponseType();

  public abstract Task @NotNull [] parseIssues(@NotNull String response, int max) throws Exception;

  public abstract @Nullable Task parseIssue(@NotNull String response) throws Exception;

  public abstract boolean isConfigured();

  @Override
  public ResponseHandler clone() {
    try {
      return (ResponseHandler) super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new AssertionError("ResponseHandler#clone() should be supported");
    }
  }
}
