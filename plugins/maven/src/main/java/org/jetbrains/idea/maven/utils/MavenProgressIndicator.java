package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.progress.ProgressIndicator;

public class MavenProgressIndicator {
  private final ProgressIndicator myIndicator;

  public MavenProgressIndicator(ProgressIndicator i) {
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

  public void setFraction(double value) {
    if (myIndicator != null) myIndicator.setFraction(value);
  }

  public boolean isCanceled() {
    return myIndicator != null && myIndicator.isCanceled();
  }

  public void checkCanceled() throws MavenProcessCanceledException {
    if (isCanceled()) throw new MavenProcessCanceledException();
  }
}
