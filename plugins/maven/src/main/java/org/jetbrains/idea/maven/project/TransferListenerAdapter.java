package org.jetbrains.idea.maven.project;

import com.intellij.openapi.progress.ProgressIndicator;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;

public class TransferListenerAdapter implements TransferListener {
  private final ProgressIndicator myIndicator;

  private String myRepository;
  private String myResource;
  private long mySize;
  private long myProgress;

  //private static long total;
  //private long started;

  public TransferListenerAdapter(ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public void transferInitiated(TransferEvent event) {
    myIndicator.checkCanceled();
    //updateProgress();
  }

  public void transferStarted(TransferEvent event) {
    myRepository = event.getWagon().getRepository().getName();
    myResource = event.getResource().getName();
    mySize = event.getResource().getContentLength();
    myProgress = 0;

    updateProgress();

    //System.out.print("Downloading " + event.getResource().getName() + " from [" + event.getWagon().getRepository().getId() + "]...");
    //started = System.currentTimeMillis();
  }

  private void updateProgress() {
    myIndicator.setText2(ProjectBundle.message("maven.transfer.progress",
                                               myProgress / 1024,
                                               mySize / 1024,
                                               myRepository,
                                               myResource));
  }

  public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    myIndicator.checkCanceled();
    myProgress += i;
    updateProgress();
  }

  public void transferCompleted(TransferEvent event) {
    addArtifactToIndex(event);
    myIndicator.checkCanceled();
    updateTiming(true);
  }

  public void transferError(TransferEvent event) {
    myIndicator.checkCanceled();
    updateTiming(false);
  }

  public void debug(String s) {
    myIndicator.checkCanceled();
  }

  private void updateTiming(boolean ok) {
    //long finished = System.currentTimeMillis();
    //long time = finished - started;
    //total += time;
    //System.out.println((ok ? "OK: " : "ERROR: ") + time + " (" + total + ")");
  }

  private void addArtifactToIndex(TransferEvent event) {
    MavenIndicesManager.getInstance().addArtifact(event.getLocalFile(),
                                                  event.getResource().getName());
  }
}
