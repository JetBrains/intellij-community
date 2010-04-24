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
package org.jetbrains.idea.maven.embedder;

import org.jetbrains.idea.maven.execution.RunnerBundle;

import java.util.List;

public class MavenConsoleHelper {
  public static void printException(MavenConsole console, Throwable throwable) {
    console.systemMessage(MavenConsole.LEVEL_ERROR, RunnerBundle.message("embedded.build.failed"), throwable);
  }

  public static void printExecutionExceptions(MavenConsole console, MavenWrapperExecutionResult result) {
    for (Exception each : (List<Exception>)result.getExceptions()) {
      Throwable cause = each.getCause();
      printException(console, cause == null ? each : cause);
    }
  }
}
