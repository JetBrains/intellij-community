package org.jetbrains.idea.maven.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;

public class Progress {
  private ProgressIndicator myIndicator;

  public Progress() {
    this(ProgressManager.getInstance().getProgressIndicator());
  }

  public Progress(ProgressIndicator i) {
    myIndicator = i;
  }

  public ProgressIndicator getIndicator() {
    return myIndicator;
  }

  public void setText(String s) {
    if (myIndicator != null) myIndicator.setText(s);
  }

  public void setText2(String s) {
    if (myIndicator != null) myIndicator.setText2(s);
  }

  public void check() throws CanceledException {
    if (myIndicator != null && myIndicator.isCanceled()) throw new CanceledException();
  }

  public void run(Project p, String title, final Process t) throws MavenException, CanceledException {
    final MavenException[] mavenEx = new MavenException[1];
    final CanceledException[] canceledEx = new CanceledException[1];

    ProgressManager.getInstance().run(new Task.Modal(p, title, true) {
      public void run(ProgressIndicator i) {
        try {
          t.run(Progress.this);
        }
        catch (MavenException e) {
          mavenEx[0] = e;
        }
        catch (CanceledException e) {
          canceledEx[0] = e;
        }
      }
    });

    if (mavenEx[0] != null) throw mavenEx[0];
    if (canceledEx[0] != null) throw canceledEx[0];
  }

  public static interface Process {
    void run(Progress p) throws MavenException, CanceledException;
  }
}
