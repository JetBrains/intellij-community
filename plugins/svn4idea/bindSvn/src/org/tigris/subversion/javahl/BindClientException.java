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
package org.tigris.subversion.javahl;

import org.jetbrains.annotations.NotNull;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/25/13
 * Time: 6:29 PM
 */
public class BindClientException extends ClientException {
  private Throwable myCause;

  public BindClientException(String message, String source, int aprError) {
    super(message, source, aprError);
  }

  public BindClientException(org.apache.subversion.javahl.ClientException ex) {
    super(ex);
  }

  public static BindClientException create(@NotNull final Throwable t, final int code) {
    final BindClientException exception = new BindClientException(t.getMessage(), null, code);
    exception.myCause = t;
    return exception;
  }

  @Override
  public Throwable getCause() {
    return myCause;
  }
}
