// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.vcs.checkout.CheckoutStrategy;

import java.io.File;

import static org.jetbrains.idea.svn.branchConfig.DefaultBranchConfig.*;

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
    if (path.getName().equalsIgnoreCase(TRUNK_NAME) && (path.getParent() != null)) {
      return new File(getSelectedLocation(), path.getParent());
    }
    path = path.getParentFile();
    if ((path != null) && path.getName().equalsIgnoreCase(BRANCHES_NAME) && (path.getParent() != null)) {
      return new File(getSelectedLocation(), path.getParent());
    }
    if ((path != null) && path.getName().equalsIgnoreCase(TAGS_NAME) && (path.getParent() != null)) {
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
