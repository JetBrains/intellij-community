package com.intellij.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public class SimpleCheckoutStrategy extends CheckoutStrategy{
  public SimpleCheckoutStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  public boolean useAlternativeCheckoutLocation() {
    return false;
  }

  public File getResult() {
    return new File(getSelectedLocation(), getCvsPath().getPath());
  }

  public File getCheckoutDirectory() {
    return getSelectedLocation();
  }
}
