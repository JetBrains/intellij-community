/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.validation.ModelValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenException extends Exception {
  private String myPomPath;
  private String mySummary;
  private final List<String> myMessages = new ArrayList<String>();

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
