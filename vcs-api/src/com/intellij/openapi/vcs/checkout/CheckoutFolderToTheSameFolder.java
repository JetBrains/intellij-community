package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public class CheckoutFolderToTheSameFolder extends CheckoutStrategy {
  public CheckoutFolderToTheSameFolder(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  public File getResult() {
    if (isForFile()) return null;
    if (getSelectedLocation().getParentFile() == null) return null;
    return getSelectedLocation();
  }

  public boolean useAlternativeCheckoutLocation() {
    return true;
  }

  public File getCheckoutDirectory() {
    return getSelectedLocation();
  }

}
