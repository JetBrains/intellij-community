/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.vcs.VcsException;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/25/13
 * Time: 5:57 PM
 *
 * Marker exception
 */
public class SvnBindException extends VcsException {
  public SvnBindException(String message) {
    super(message);
  }

  public SvnBindException(Throwable throwable, boolean isWarning) {
    super(throwable, isWarning);
  }

  public SvnBindException(Throwable throwable) {
    super(throwable);
  }

  public SvnBindException(String message, Throwable cause) {
    super(message, cause);
  }

  public SvnBindException(String message, boolean isWarning) {
    super(message, isWarning);
  }

  public SvnBindException(Collection<String> messages) {
    super(messages);
  }
}
