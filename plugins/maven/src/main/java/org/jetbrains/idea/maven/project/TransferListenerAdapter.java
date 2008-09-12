package org.jetbrains.idea.maven.project;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;

import java.io.File;
import java.util.List;

public class TransferListenerAdapter implements TransferListener {
  private ProgressIndicator myIndicator;

  private Wagon myWagon;
  private String myResource;
  private long mySize;
  private long myProgress;

  //private static long total;
  //private long started;

  public TransferListenerAdapter() {
    myIndicator = getProgressIndicator();
  }

  private ProgressIndicator getProgressIndicator() {
    ProgressIndicator i = ProgressManager.getInstance().getProgressIndicator();
    return i == null ? new EmptyProgressIndicator() : i;
  }

  public void transferInitiated(TransferEvent event) {
    myIndicator.checkCanceled();
    myIndicator.setText2(ProjectBundle.message("maven.transfer.start",
                                               event.getWagon().getRepository().getName(),
                                               event.getResource().getName()));
    //System.out.print("Downloading " + event.getResource().getName() + "...");
    //started = System.currentTimeMillis();
  }

  public void transferStarted(TransferEvent event) {
    myIndicator.checkCanceled();
    myWagon = event.getWagon();

    myResource = event.getResource().getName();
    mySize = event.getResource().getContentLength();
    myProgress = 0;

    updateProgress();
  }

  private void updateProgress() {
    myIndicator.setText2(ProjectBundle.message("maven.transfer.progress",
                                               myProgress / 1024,
                                               mySize / 1024,
                                               myWagon.getRepository().getName(),
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
    myIndicator.setText2("");
    //updateTiming(true);
  }

  public void transferError(TransferEvent event) {
    myIndicator.checkCanceled();
    myIndicator.setText2("");
    //updateTiming(false);
  }

  public void debug(String s) {
    myIndicator.checkCanceled();
  }

  //private void updateTiming(boolean ok) {
  //  long finished = System.currentTimeMillis();
  //  long time = finished - started;
  //  total += time;
  //  System.out.println((ok ? "OK: " : "ERROR: ") + time + " (" + total+  ")");
  //}

  private void addArtifactToIndex(TransferEvent event) {
    if (getArtifactParts(event).size() < 3) return;
    MavenIndicesManager.getInstance().addArtifact(getRepositoryFile(event),
                                                  getMavenId(event));
  }

  private File getRepositoryFile(TransferEvent event) {
    List<String> parts = getArtifactParts(event);

    File result = event.getLocalFile();
    for (int i = 0; i < parts.size(); i++) {
      result = result.getParentFile();
    }
    return result;
  }

  private MavenId getMavenId(TransferEvent event) {
    List<String> parts = getArtifactParts(event);

    String groupId = "";
    for (int i = 0; i < parts.size() - 3; i++) {
      if (groupId.length() > 0) groupId += ".";
      groupId += parts.get(i);
    }

    String artifactId = parts.get(parts.size() - 3);
    String version = parts.get(parts.size() - 2);

    return new MavenId(groupId, artifactId, version);
  }

  private List<String> getArtifactParts(TransferEvent event) {
    return StringUtil.split(event.getResource().getName(), "/");
  }
}