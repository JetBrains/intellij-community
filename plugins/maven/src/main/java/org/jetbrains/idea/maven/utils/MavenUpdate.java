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
package org.jetbrains.idea.maven.utils;

import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;

public abstract class MavenUpdate extends Update {
  public MavenUpdate(@NonNls Object identity) {
    super(identity);
  }

  public final void run() {
    try {
      doRun();
    }
    catch (Throwable e) {
      e.printStackTrace();
      MavenLog.LOG.error(e);
    }
  }

  public abstract void doRun();
}
