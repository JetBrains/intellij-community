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
package org.jetbrains.idea.maven.facade.embedder;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;

import java.text.MessageFormat;

public class TransferListenerAdapter implements TransferListener {
  protected final MavenFacadeProgressIndicatorWrapper myIndicator;

  private String myRepository;
  private String myResource;
  private long mySize;
  private long myProgress;

  public TransferListenerAdapter(MavenFacadeProgressIndicatorWrapper indicator) {
    myIndicator = indicator;
  }

  public void transferInitiated(TransferEvent event) {
    checkCanceled();
  }

  private void checkCanceled() {
    myIndicator.checkCanceled();
  }

  public void transferStarted(TransferEvent event) {
    myRepository = event.getWagon().getRepository().getName();
    myResource = event.getResource().getName();
    mySize = event.getResource().getContentLength();
    myProgress = 0;

    updateProgress();
  }

  private void updateProgress() {
    doUpdateProgress(myProgress / 1024, mySize / 1024);
  }

  protected void doUpdateProgress(long downloaded, long total) {
    myIndicator.setText2(MessageFormat.format("{0}/{1}Kb  [{2}] {3}", downloaded, total == 0 ? "?" : total, myRepository, myResource));
  }

  public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    checkCanceled();
    myProgress += i;
    updateProgress();
  }

  public void transferCompleted(TransferEvent event) {
    addArtifactToIndex(event);
    checkCanceled();
  }

  public void transferError(TransferEvent event) {
    checkCanceled();
  }

  public void debug(String s) {
    checkCanceled();
  }

  private void addArtifactToIndex(TransferEvent event) {
    // todo 
    //MavenIndicesManager.getInstance().addArtifact(event.getLocalFile(),
    //                                              event.getResource().getName());
  }
}
