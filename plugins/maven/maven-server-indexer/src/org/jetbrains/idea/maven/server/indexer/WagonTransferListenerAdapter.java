// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import com.intellij.openapi.util.text.StringUtilRt;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.idea.maven.server.MavenProcessCanceledRuntimeException;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.RuntimeRemoteException;

import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WagonTransferListenerAdapter implements TransferListener {
  protected final MavenServerProgressIndicator myIndicator;
  private final Map<String, DownloadData> myDownloads = new ConcurrentHashMap<String, DownloadData>();

  public WagonTransferListenerAdapter(MavenServerProgressIndicator indicator) {
    myIndicator = indicator;
  }

  @Override
  public void transferInitiated(TransferEvent event) {
    checkCanceled();
  }

  private void checkCanceled() {
    try {
      if (myIndicator.isCanceled()) throw new MavenProcessCanceledRuntimeException();
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  @Override
  public void transferStarted(TransferEvent event) {
    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = new DownloadData(event.getWagon().getRepository().getName(),
                                         event.getResource().getContentLength());
    myDownloads.put(resourceName, data);
    updateProgress(resourceName, data);
  }

  @Override
  public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = myDownloads.get(resourceName);
    data.downloaded += i;
    updateProgress(resourceName, data);
  }

  @Override
  public void transferCompleted(TransferEvent event) {
    MavenServerGlobals.getDownloadListener().artifactDownloaded(event.getLocalFile(), event.getResource().getName());

    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = myDownloads.remove(resourceName);
    data.finished = true;
    updateProgress(resourceName, data);
  }

  @Override
  public void transferError(TransferEvent event) {
    checkCanceled();

    String resourceName = event.getResource().getName();
    DownloadData data = myDownloads.remove(resourceName);
    if (data != null) {
      data.failed = true;
      updateProgress(resourceName, data);
    }
  }

  @Override
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
      sizeInfo = StringUtilRt.formatFileSize(data.downloaded);
    }
    else {
      sizeInfo = ((int)100f * data.downloaded / data.total) + "% of " + StringUtilRt.formatFileSize(data.total);
    }

    try {
      myIndicator.setText2(MessageFormat.format(prefix + sizeInfo + " [{0}] {1}", data.repository, resourceName));
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }

    downloadProgress(data.downloaded, data.total);
  }

  private void downloadProgress(long downloaded, long total) {
    try {
      myIndicator.setFraction(((double)downloaded) / total);
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  private static final class DownloadData {
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
