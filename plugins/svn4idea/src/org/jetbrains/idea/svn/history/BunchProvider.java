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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.Nullable;

public interface BunchProvider {
  long getEarliestRevision();

  /**
   * @param desirableSize - not the size that should be returned. it is the size that should be found, and then start or/and end
   * bound revisions might be removed from it.
   * so, if need maximum 50 revisions between 10 and 100, not including 10 and 100 revisions, arguments should be:
   * 100, 10, 52, false, false
   */
  @Nullable
  Fragment getEarliestBunchInInterval(final long earliestRevision, final long oldestRevision, final int desirableSize,
                                      final boolean includeYoungest, final boolean includeOldest) throws VcsException;
  boolean isEmpty();
}
