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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class SvnFormatSelector implements ISVNAdminAreaFactorySelector {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnFormatSelector");

  public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return factories;
    }

    if (! writeAccess) {
      return factories;
    }

    Collection result = null;
    final WorkingCopyFormat presetFormat = SvnWorkingCopyFormatHolder.getPresetFormat();
    if (presetFormat != null) {
      result = format2Factories(presetFormat, factories);
    }

    if (result == null) {
      final WorkingCopyFormat format = getWorkingCopyFormat(path);
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
    } else if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
      return factoriesFor16(factories);
    } else if (WorkingCopyFormat.ONE_DOT_FIVE.equals(format)) {
      return factoriesFor15(factories);
    } else if (WorkingCopyFormat.ONE_DOT_FOUR.equals(format)) {
      return factoriesFor14(factories);
    } else if (WorkingCopyFormat.ONE_DOT_THREE.equals(format)) {
      return factoriesFor13(factories);
    }
    return null;
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor13(final Collection factories) {
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
      final int supportedVersion = factory.getSupportedVersion();
      if (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion) {
        return Collections.singletonList(factory);
      }
    }
    return Collections.emptyList();
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor14(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<SVNAdminAreaFactory>(2);
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
      final int supportedVersion = factory.getSupportedVersion();
      if ((WorkingCopyFormat.ONE_DOT_FOUR.getFormat() == supportedVersion) ||
          (WorkingCopyFormat.ONE_DOT_THREE.getFormat() == supportedVersion)) {
        result.add(factory);
      }
    }
    return result;
  }

  private static Collection<SVNAdminAreaFactory> factoriesFor15(final Collection factories) {
    final Collection<SVNAdminAreaFactory> result = new ArrayList<SVNAdminAreaFactory>(2);
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
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
    final Collection<SVNAdminAreaFactory> result = new ArrayList<SVNAdminAreaFactory>(2);
    for (Iterator iterator = factories.iterator(); iterator.hasNext();) {
      final SVNAdminAreaFactory factory = (SVNAdminAreaFactory) iterator.next();
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

  @NotNull
  public static WorkingCopyFormat findRootAndGetFormat(final File path) {
    File root = SvnUtil.getWorkingCopyRootNew(path);

    return root != null ? getWorkingCopyFormat(root) : WorkingCopyFormat.UNKNOWN;
  }

  @NotNull
  public static WorkingCopyFormat getWorkingCopyFormat(final File path) {
    WorkingCopyFormat format = SvnUtil.getFormat(path);

    return WorkingCopyFormat.UNKNOWN.equals(format) ? detectWithSvnKit(path) : format;
  }

  @NotNull
  private static WorkingCopyFormat detectWithSvnKit(File path) {
    try {
      final SvnWcGeneration svnWcGeneration = SvnOperationFactory.detectWcGeneration(path, true);
      if (SvnWcGeneration.V17.equals(svnWcGeneration)) return WorkingCopyFormat.ONE_DOT_SEVEN;
    }
    catch (SVNException e) {
      //
    }
    int format  = 0;
    // it is enough to check parent and this.
    try {
      format = SVNAdminAreaFactory.checkWC(path, false);
    } catch (SVNException e) {
      //
    }
    try {
      if (format == 0 && path.getParentFile() != null) {
        format = SVNAdminAreaFactory.checkWC(path.getParentFile(), false);
      }
    } catch (SVNException e) {
      //
    }

    return WorkingCopyFormat.getInstance(format);
  }
}
