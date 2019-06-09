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
import com.intellij.util.ExceptionUtilRt;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;
import org.sonatype.aether.transfer.TransferResource;

import java.io.File;
import java.rmi.RemoteException;

/**
 * @author Sergey Evdokimov
 */
public class Maven30TransferListenerAdapter implements TransferListener {

  protected final MavenServerProgressIndicator myIndicator;

  public Maven30TransferListenerAdapter(MavenServerProgressIndicator indicator) {
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
      myIndicator.startedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, formatResourceName(event));
      myIndicator.setIndeterminate(true);
      myIndicator.setText2(formatResourceName(event));
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
  public void transferCorrupted(TransferEvent event) {
    try {
      myIndicator.setText2("Checksum failed: " + formatResourceName(event));
      myIndicator.setIndeterminate(true);
      myIndicator.failedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, formatResourceName(event), "Checksum failed", null);
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
      myIndicator.completedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, formatResourceName(event));

      Maven3ServerGlobals.getDownloadListener().artifactDownloaded(event.getResource().getFile(), event.getResource().getResourceName());
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
        myIndicator.failedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, formatResourceName(event), "Cancelled", null);
        return; // Don't throw exception here.
      }
    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }

    try {
      myIndicator.setText2("Failed to download " + formatResourceName(event));
      myIndicator.setIndeterminate(true);
      if (event.getException() != null) {
        String stackTrace = ExceptionUtilRt.getThrowableText(event.getException(), "com.intellij.");
        myIndicator.failedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, formatResourceName(event),
                                   event.getException().getMessage(), stackTrace);
      }
      else {
        myIndicator
          .failedDownload(MavenServerProgressIndicator.ResolveType.DEPENDENCY, formatResourceName(event), "Failed to download", null);
      }

    }
    catch (RemoteException e) {
      throw new RuntimeRemoteException(e);
    }
  }
}
