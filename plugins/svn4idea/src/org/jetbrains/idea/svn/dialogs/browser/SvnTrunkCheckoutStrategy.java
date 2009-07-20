package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.vcs.checkout.CheckoutStrategy;

import java.io.File;

/**
 * @author yole
 */
public class SvnTrunkCheckoutStrategy extends CheckoutStrategy {
  public SvnTrunkCheckoutStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  @Override
  public File getResult() {
    if (getCvsPath().getName().equals("trunk")) {
      return new File(getSelectedLocation(), getCvsPath().getParent());
    }
    return null;
  }

  @Override
  public boolean useAlternativeCheckoutLocation() {
    return true;
  }

  @Override
  public File getCheckoutDirectory() {
    return getSelectedLocation();
  }
}
