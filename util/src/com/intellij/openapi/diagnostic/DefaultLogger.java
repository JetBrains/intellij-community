/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;

public class DefaultLogger extends Logger {
  public DefaultLogger(String category) {
  }

  public boolean isDebugEnabled() {
    return false;
  }

  public void debug(String message) {
  }

  public void debug(Throwable t) {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void error(String message, Throwable t, String[] details) {
    System.err.println("ERROR: " + message);
    t.printStackTrace();
    if (details != null && details.length > 0) {
      System.out.println("details: ");
      for (int i = 0; i < details.length; i++) {
        System.out.println(details[i]);
      }
    }

    throw new AssertionError(message);
  }

  public void info(String message) {
  }

  public void info(String message, Throwable t) {
  }

  public void setLevel(Level level) {
  }
}