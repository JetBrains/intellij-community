package org.jetbrains.idea.maven.project;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public class TransferListenerAdapter implements TransferListener {
  protected final MavenProgressIndicator myIndicator;

  private String myRepository;
  private String myResource;
  private long mySize;
  private long myProgress;

  //private static long total;
  //private long started;

  public TransferListenerAdapter(MavenProgressIndicator indicator) {
    myIndicator = indicator;
  }

  public void transferInitiated(TransferEvent event) {
    myIndicator.checkCanceledNative();
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
    myIndicator.setText2(ProjectBundle.message("maven.transfer.progress", downloaded, total, myRepository, myResource));
  }

  public void transferProgress(TransferEvent event, byte[] bytes, int i) {
    myIndicator.checkCanceledNative();
    myProgress += i;
    updateProgress();
  }

  public void transferCompleted(TransferEvent event) {
    addArtifactToIndex(event);
    myIndicator.checkCanceledNative();
    updateTiming(true);
  }

  public void transferError(TransferEvent event) {
    myIndicator.checkCanceledNative();
    updateTiming(false);
  }

  public void debug(String s) {
    myIndicator.checkCanceledNative();
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
