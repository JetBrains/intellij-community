/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.mergeinfo;

import icons.SvnIcons;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 3/30/13
* Time: 2:41 PM
*/
public enum ListMergeStatus {
  COMMON(SvnIcons.Common),
  MERGED(SvnIcons.Integrated),
  NOT_MERGED(SvnIcons.Notintegrated),
  ALIEN(null),
  REFRESHING(SvnIcons.IntegrationStatusUnknown);

  @Nullable
  private final Icon myIcon;

  ListMergeStatus(@Nullable final Icon icon) {
    myIcon = icon;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @Contract(value = "null -> null; !null -> !null", pure = true)
  public static ListMergeStatus from(@Nullable SvnMergeInfoCache.MergeCheckResult mergeCheckResult) {
    ListMergeStatus result = null;

    if (mergeCheckResult != null) {
      switch (mergeCheckResult) {
        case MERGED:
          result = MERGED;
          break;
        case COMMON:
          result = COMMON;
          break;
        default:
          result = NOT_MERGED;
      }
    }

    return result;
  }
}
