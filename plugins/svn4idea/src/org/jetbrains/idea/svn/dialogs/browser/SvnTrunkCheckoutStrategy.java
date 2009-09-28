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
    File path = getCvsPath();
    if (path.getName().equalsIgnoreCase("trunk") && (path.getParent() != null)) {
      return new File(getSelectedLocation(), path.getParent());
    }
    path = path.getParentFile();
    if ((path != null) && path.getName().equalsIgnoreCase("branches") && (path.getParent() != null)) {
      return new File(getSelectedLocation(), path.getParent());
    }
    if ((path != null) && path.getName().equalsIgnoreCase("tags") && (path.getParent() != null)) {
      return new File(getSelectedLocation(), path.getParent());
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
