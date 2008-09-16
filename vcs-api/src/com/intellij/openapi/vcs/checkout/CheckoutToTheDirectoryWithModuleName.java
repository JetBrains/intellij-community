package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public class CheckoutToTheDirectoryWithModuleName extends CheckoutStrategy{
  public CheckoutToTheDirectoryWithModuleName(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  public File getResult() {
    if (!getSelectedLocation().getName().equals(getTopLevelName(getCvsPath()))) return null;
    return new File(getSelectedLocation().getParentFile(), getCvsPath().getPath());
  }

  private String getTopLevelName(File cvsPath) {
    File current = cvsPath;
    while(current.getParentFile() != null) current = current.getParentFile();
    return current.getName();
  }

  public boolean useAlternativeCheckoutLocation() {
    return false;
  }

  public File getCheckoutDirectory() {
    return getSelectedLocation().getParentFile();
  }
}
