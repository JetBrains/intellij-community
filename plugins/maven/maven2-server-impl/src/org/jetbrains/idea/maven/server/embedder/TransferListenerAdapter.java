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
package org.jetbrains.idea.maven.server.embedder;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.idea.maven.server.Maven2ServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerDownloadListener;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;

import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.Map;

public class TransferListenerAdapter implements TransferListener {
  protected final MavenServerProgressIndicator myIndicator;
  private final Map<String, DownloadData> myDownloads = ContainerUtil.newConcurrentMap();

  public TransferListenerAdapter(MavenServerProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public void transferInitiated(TransferEvent event) {
    checkCanceled();
  }

  private void checkCanceled() {
    try {
      if (myIndicator.isCanceled()) throw new ProcessCanceledException();
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  public void transferStarted(TransferEvent event) {
    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = new DownloadData(event.getWagon().getRepository().getName(),
                                         event.getResource().getContentLength());
    myDownloads.put(resourceName, data);
    updateProgress(resourceName, data);
  }

  public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = myDownloads.get(resourceName);
    data.downloaded += i;
    updateProgress(resourceName, data);
  }

  public void transferCompleted(TransferEvent event) {
    try {
      MavenServerDownloadListener listener = Maven2ServerGlobals.getDownloadListener();
      if (listener != null) listener.artifactDownloaded(event.getLocalFile(), event.getResource().getName());
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }

    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = myDownloads.remove(resourceName);
    data.finished = true;
    updateProgress(resourceName, data);
  }

  public void transferError(TransferEvent event) {
    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = myDownloads.remove(resourceName);
    if (data != null) {
      data.failed = true;
      updateProgress(resourceName, data);
    }
  }

  public void debug(String s) {
    checkCanceled();
  }

  private void updateProgress(String resourceName, DownloadData data) {
    String prefix = "";
    if (data.finished) {
      prefix = "Finished ";
    }
    if (data.failed) {
      prefix = "Failed ";
    }

    String sizeInfo;
    if (data.finished || data.failed || data.total <= 0) {
      sizeInfo = StringUtil.formatFileSize(data.downloaded);
    } else {
      sizeInfo = ((int)100f * data.downloaded / data.total) + "% of " + StringUtil.formatFileSize(data.total);
    }

    try {
      myIndicator.setText2(MessageFormat.format(prefix + sizeInfo + " [{0}] {1}", data.repository, resourceName));
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }

    downloadProgress(data.downloaded, data.total);
  }

  protected void downloadProgress(long downloaded, long total) {
  }

  private static class DownloadData {
    public final String repository;
    public final long total;
    public long downloaded;
    public boolean finished;
    public boolean failed;

    private DownloadData(String repository, long total) {
      this.repository = repository;
      this.total = total;
    }
  }
}
