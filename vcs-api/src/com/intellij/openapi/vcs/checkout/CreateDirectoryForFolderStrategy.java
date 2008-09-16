package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public class CreateDirectoryForFolderStrategy extends CheckoutStrategy{
  public CreateDirectoryForFolderStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  public File getResult() {
    if (isForFile() && (getSelectedLocation().getParentFile() == null)) return null;
    return new File(getSelectedLocation(), getCvsPath().getName());
  }

  public boolean useAlternativeCheckoutLocation() {
    return true;
  }

  public File getCheckoutDirectory() {
    if (isForFile()){
      return getSelectedLocation();
    } else {
      return new File(getSelectedLocation(), getCvsPath().getName());
    }
  }

}
