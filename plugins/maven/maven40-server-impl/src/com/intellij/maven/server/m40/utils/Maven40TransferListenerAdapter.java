// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.openapi.util.text.StringUtilRt;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;
import org.jetbrains.idea.maven.server.MavenProcessCanceledRuntimeException;
import org.jetbrains.idea.maven.server.MavenServerGlobals;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.RuntimeRemoteException;

import java.io.File;
import java.rmi.RemoteException;

public class Maven40TransferListenerAdapter implements TransferListener {

  protected final MavenServerProgressIndicator myIndicator;

  public Maven40TransferListenerAdapter(MavenServerProgressIndicator indicator) {
    myIndicator = indicator;
  }

  private void checkCanceled() {
    try {
      if (myIndicator.isCanceled()) throw new MavenProcessCanceledRuntimeException();
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  private static String formatResourceName(TransferEvent event) {
    TransferResource resource = event.getResource();
    File file = resource.getFile();
    return (file == null ? resource.getResourceName() : file.getName()) + " [" + resource.getRepositoryUrl() + "]";
  }

  @Override
  public void transferInitiated(TransferEvent event) {
    checkCanceled();
    try {
      String eventString = formatResourceName(event);
      myIndicator.setIndeterminate(true);
      myIndicator.setText2(eventString);
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  @Override
  public void transferStarted(TransferEvent event) throws TransferCancelledException {
    transferProgressed(event);
  }

  @Override
  public void transferProgressed(TransferEvent event) {
    checkCanceled();

    TransferResource r = event.getResource();

    long totalLength = r.getContentLength();

    String sizeInfo;
    if (totalLength <= 0) {
      sizeInfo = StringUtilRt.formatFileSize(event.getTransferredBytes()) + " / ?";
    } else {
      sizeInfo = StringUtilRt.formatFileSize(event.getTransferredBytes()) + " / " + StringUtilRt.formatFileSize(totalLength);
    }

    try {
      myIndicator.setText2(formatResourceName(event) + "  (" + sizeInfo + ')');
      if (totalLength <= 0) {
        myIndicator.setIndeterminate(true);
      }
      else {
        myIndicator.setIndeterminate(false);
        myIndicator.setFraction((double)event.getTransferredBytes() / totalLength);
      }
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  @Override
  public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
    try {
      myIndicator.setText2("Checksum failed: " + formatResourceName(event));
      myIndicator.setIndeterminate(true);
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  @Override
  public void transferSucceeded(TransferEvent event) {
    try {
      myIndicator.setText2("Finished (" + StringUtilRt.formatFileSize(event.getTransferredBytes()) + ") " + formatResourceName(event));
      myIndicator.setIndeterminate(true);
      MavenServerGlobals.getDownloadListener().artifactDownloaded(event.getResource().getFile(), event.getResource().getResourceName());
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }

  @Override
  public void transferFailed(TransferEvent event) {
    try {
      if (myIndicator.isCanceled()) {
        myIndicator.setText2("Canceling...");
        return; // Don't throw exception here.
      }
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }

    try {
      myIndicator.setText2("Failed to download " + formatResourceName(event));
      myIndicator.setIndeterminate(true);
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }
}
