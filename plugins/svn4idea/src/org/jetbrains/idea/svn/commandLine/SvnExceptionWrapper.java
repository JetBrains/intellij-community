/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import org.tmatesoft.svn.core.SVNException;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 12:54 PM
 */
public class SvnExceptionWrapper extends RuntimeException {
  /**
   * Constructs a new runtime exception with the specified cause and a
   * detail message of <tt>(cause==null ? null : cause.toString())</tt>
   * (which typically contains the class and detail message of
   * <tt>cause</tt>).  This constructor is useful for runtime exceptions
   * that are little more than wrappers for other throwables.
   *
   * @param cause the cause (which is saved for later retrieval by the
   *              {@link #getCause()} method).  (A <tt>null</tt> value is
   *              permitted, and indicates that the cause is nonexistent or
   *              unknown.)
   * @since 1.4
   */
  public SvnExceptionWrapper(SVNException cause) {
    super(cause);
  }

  public SvnExceptionWrapper(SvnBindException cause) {
    super(cause);
  }
}
