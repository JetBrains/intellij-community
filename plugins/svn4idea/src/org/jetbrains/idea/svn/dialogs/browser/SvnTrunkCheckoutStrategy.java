/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
