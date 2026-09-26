// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import com.intellij.openapi.util.text.StringUtilRt;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transfer.TransferResource;
import org.jetbrains.idea.maven.server.MavenProcessCanceledRuntimeException;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicatorImpl;

import java.io.File;

public class Maven40TransferListenerAdapter implements TransferListener {

  protected final MavenServerConsoleIndicatorImpl myIndicator;

  public Maven40TransferListenerAdapter(MavenServerConsoleIndicatorImpl indicator) {
    myIndicator = indicator;
  }

  private void checkCanceled() {
    if (myIndicator.isCanceled()) throw new MavenProcessCanceledRuntimeException();
  }

  private static String formatResourceName(TransferEvent event) {
    TransferResource resource = event.getResource();
    File file = resource.getFile();
    return (file == null ? resource.getResourceName() : file.getName()) + " [" + resource.getRepositoryUrl() + "]";
  }

  @Override
  public void transferInitiated(TransferEvent event) {
    checkCanceled();
    String eventString = formatResourceName(event);
    myIndicator.debug(eventString);
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
    }
    else {
      sizeInfo = StringUtilRt.formatFileSize(event.getTransferredBytes()) + " / " + StringUtilRt.formatFileSize(totalLength);
    }

    myIndicator.debug(formatResourceName(event) + "  (" + sizeInfo + ')');
    if (totalLength > 0) {
      myIndicator.debug(String.valueOf(Math.floor(100 * (double)event.getTransferredBytes() / totalLength)) + "%");
    }
  }

  @Override
  public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
    myIndicator.warn("Checksum failed: " + formatResourceName(event));
  }

  @Override
  public void transferSucceeded(TransferEvent event) {
    myIndicator.debug("Finished (" + StringUtilRt.formatFileSize(event.getTransferredBytes()) + ") " + formatResourceName(event));
    myIndicator.artifactDownloaded(event.getResource().getPath().toFile());
  }

  @Override
  public void transferFailed(TransferEvent event) {
    if (myIndicator.isCanceled()) {
      myIndicator.info("Canceling...");
      return; // Don't throw exception here.
    }

    myIndicator.warn("Failed to download " + formatResourceName(event));
  }
}
