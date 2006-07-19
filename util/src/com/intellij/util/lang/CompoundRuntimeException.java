/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.util.lang;

import java.util.List;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * @author mike
 */
public class CompoundRuntimeException extends RuntimeException {
  private final List<Throwable> myThrowables;


  public CompoundRuntimeException(final List<Throwable> throwables) {
    //noinspection HardCodedStringLiteral
    super("Several Exceptions occured");
    myThrowables = throwables;
  }


  public void printStackTrace(PrintStream s) {
    for (Throwable throwable : myThrowables) {
      throwable.printStackTrace(s);
    }
  }


  public void printStackTrace(PrintWriter s) {
    for (Throwable throwable : myThrowables) {
      throwable.printStackTrace(s);
    }
  }
}
