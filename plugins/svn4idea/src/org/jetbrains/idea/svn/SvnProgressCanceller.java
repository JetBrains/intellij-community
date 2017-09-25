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
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class SvnProgressCanceller implements ProgressTracker {
  @Nullable private final ProgressIndicator myIndicator;

  public SvnProgressCanceller() {
    this(null);
  }

  public SvnProgressCanceller(@Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public void checkCancelled() throws SVNCancelException {
    ProgressIndicator indicator = chooseNotNull(myIndicator, ProgressManager.getInstance().getProgressIndicator());
    if (indicator != null && indicator.isCanceled()) {
      throw new SVNCancelException();
    }
  }

  public void consume(final ProgressEvent event) {
  }
}
