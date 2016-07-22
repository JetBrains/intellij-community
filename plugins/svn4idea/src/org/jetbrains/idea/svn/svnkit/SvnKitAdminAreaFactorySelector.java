/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnFormatSelector;
import org.jetbrains.idea.svn.SvnWorkingCopyFormatHolder;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitAdminAreaFactorySelector implements ISVNAdminAreaFactorySelector {

  public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return factories;
    }

    if (!writeAccess) {
      return factories;
    }

    Collection result = null;
    final WorkingCopyFormat presetFormat = SvnWorkingCopyFormatHolder.getPresetFormat();
    if (presetFormat != null) {
      result = format2Factories(presetFormat, factories);
    }

    if (result == null) {
      final WorkingCopyFormat format = SvnFormatSelector.getWorkingCopyFormat(path);
      result = format2Factories(format, factories);
    }

    if (result == null) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY));
    }
    return result;
  }

  @Nullable
  static Collection format2Factories(final WorkingCopyFormat format, final Collection factories) {
    if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      return factories;
    }
    else if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
      return factoriesFor16(factories);
    }
    else if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
      return factoriesFor15(factories);
    }
    else if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
      return factoriesFor14(factories);
    }
    else if (WorkingCopyFormat.ONE_DOT_THREE.equals(format)) {
      return factoriesFor13(factories);
    }
    return null;
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor13(final Collection factories) {
    for (Object item : factories) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory)item;
      final int supportedVersion = factory.getSupportedVersion();
      if (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) {
        return Collections.singletonList(factory);
      }
    }
    return Collections.emptyList();
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor14(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<>(2);
    for (Object item : factories) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory)item;
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion)) {
        result.add(factory);
      }
    }
    return result;
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor15(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<>(2);
    for (Object item : factories) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory)item;
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_FIVE.getFormat() == supportedVersion)) {
        result.add(factory);
      }
    }
    return result;
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor16(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<>(2);
    for (Object item : factories) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory)item;
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_FIVE.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_SIX.getFormat() == supportedVersion)) {
        result.add(factory);
      }
    }
    return result;
  }
}
