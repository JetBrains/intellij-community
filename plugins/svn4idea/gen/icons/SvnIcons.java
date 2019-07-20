// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;

import javax.swing.*;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class SvnIcons {
  private static Icon load(String path) {
    return IconManager.getInstance().getIcon(path, SvnIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconManager.getInstance().getIcon(path, clazz);
  }

  /** 16x16 */ public static final Icon Common = load("/icons/Common.svg");
  /** 16x16 */ public static final Icon Conflictc = load("/icons/conflictc.svg");
  /** 16x16 */ public static final Icon Conflictcp = load("/icons/conflictcp.svg");
  /** 16x16 */ public static final Icon Conflictct = load("/icons/conflictct.svg");
  /** 16x16 */ public static final Icon Conflictctp = load("/icons/conflictctp.svg");
  /** 16x16 */ public static final Icon Conflictp = load("/icons/conflictp.svg");
  /** 16x16 */ public static final Icon Conflictt = load("/icons/conflictt.svg");
  /** 16x16 */ public static final Icon Conflicttp = load("/icons/conflicttp.svg");
  /** 16x16 */ public static final Icon Integrated = load("/icons/Integrated.svg");
  /** 16x16 */ public static final Icon MarkAsMerged = load("/icons/MarkAsMerged.svg");
  /** 16x16 */ public static final Icon MarkAsNotMerged = load("/icons/MarkAsNotMerged.svg");
  /** 16x16 */ public static final Icon Notintegrated = load("/icons/Notintegrated.svg");
  /** 16x16 */ public static final Icon PreviewDetailsLeft = load("/icons/previewDetailsLeft.svg");
  /** 16x16 */ public static final Icon UndoIntegrateToBranch = load("/icons/UndoIntegrateToBranch.svg");

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Nodes.Unknown */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon IntegrationStatusUnknown = load("/nodes/unknown.svg", com.intellij.icons.AllIcons.class);
}
