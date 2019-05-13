/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.rollback;

import java.io.File;

/**
* @author Konstantin Kolosovsky.
*/
public class ThroughRenameInfo {

  private final File myParentImmediateReverted;
  private final File myTo;
  private final File myFirstTo;
  private final File myFrom;
  private final boolean myVersioned;

  ThroughRenameInfo(File parentImmediateReverted, File to, File firstTo, File from, boolean versioned) {
    myParentImmediateReverted = parentImmediateReverted;
    myTo = to;
    myFrom = from;
    myVersioned = versioned;
    myFirstTo = firstTo;
  }

  public File getFirstTo() {
    return myFirstTo;
  }

  public boolean isVersioned() {
    return myVersioned;
  }

  public File getParentImmediateReverted() {
    return myParentImmediateReverted;
  }

  public File getTo() {
    return myTo;
  }

  public File getFrom() {
    return myFrom;
  }
}
