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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.idea.svn.CommitEventHandler;
import org.jetbrains.idea.svn.CommitEventType;
import org.jetbrains.idea.svn.SvnBundle;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/26/13
 * Time: 11:13 AM
 */
public class IdeaCommitHandler implements CommitEventHandler {
  private final ProgressIndicator myPi;

  public IdeaCommitHandler(ProgressIndicator pi) {
    myPi = pi;
  }

  @Override
  public void commitEvent(CommitEventType type, File target) {
    if (myPi == null) return;
    myPi.checkCanceled();

    if (CommitEventType.adding.equals(type)) {
      myPi.setText2(SvnBundle.message("progress.text2.adding", target));
    } else if (CommitEventType.deleting.equals(type)) {
      myPi.setText2(SvnBundle.message("progress.text2.deleting", target));
    } else if (CommitEventType.sending.equals(type)) {
      myPi.setText2(SvnBundle.message("progress.text2.sending", target));
    } else if (CommitEventType.replacing.equals(type)) {
      myPi.setText2(SvnBundle.message("progress.text2.replacing", target));
    } else if (CommitEventType.transmittingDeltas.equals(type)) {
      myPi.setText2(SvnBundle.message("progress.text2.transmitting.delta", target));
    }
  }

  @Override
  public void committedRevision(long revNum) {
    if (myPi == null) return;
    myPi.checkCanceled();
    myPi.setText2(SvnBundle.message("status.text.comitted.revision", revNum));
  }
}
