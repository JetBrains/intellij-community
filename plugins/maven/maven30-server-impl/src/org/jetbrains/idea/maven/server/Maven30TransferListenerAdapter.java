/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.util.text.StringUtilRt;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.aether.transfer.TransferResource;

import java.io.File;

public class Maven30TransferListenerAdapter implements TransferListener {

  protected final MavenServerProgressIndicatorWrapper myIndicator;

  public Maven30TransferListenerAdapter(MavenServerProgressIndicatorWrapper indicator) {
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
    MavenServerGlobals.getDownloadListener().artifactDownloaded(event.getResource().getFile(), event.getResource().getResourceName());
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
