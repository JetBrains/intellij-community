package org.jetbrains.idea.maven.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;

public class TransferListenerAdapter implements TransferListener {
  private ProgressIndicator indicator;
  private Wagon wagon;
  private String resource;
  private long size;
  private long progress;

  public TransferListenerAdapter(ProgressIndicator i) {
    indicator = i;
  }

  public void transferInitiated(TransferEvent event) {
    checkCanceled();
    indicator.setText2(ProjectBundle.message("maven.transfer.start",
                                             event.getWagon().getRepository().getName(),
                                             event.getResource().getName()));
  }

  public void transferStarted(TransferEvent event) {
    checkCanceled();
    wagon = event.getWagon();
    resource = event.getResource().getName();
    size = event.getResource().getContentLength();
    progress = 0;
    updateProgress();
  }

  private void updateProgress() {
    indicator.setText2(ProjectBundle.message("maven.transfer.progress",
                                             progress / 1024,
                                             size / 1024,
                                             wagon.getRepository().getName(),
                                             resource));
  }

  public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    checkCanceled();
    progress += i;
    updateProgress();
  }

  public void transferCompleted(TransferEvent event) {
    checkCanceled();
    indicator.setText2("");
  }

  public void transferError(TransferEvent event) {
    checkCanceled();
  }

  public void debug(String s) {
    checkCanceled();
  }

  private void checkCanceled() {
    if (indicator.isCanceled()) throw new ProcessCanceledException();
  }
}
