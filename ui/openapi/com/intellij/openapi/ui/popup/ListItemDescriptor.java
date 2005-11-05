/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import javax.swing.*;

/**
 * @author kir
 */
public interface ListItemDescriptor {
  String getTextFor(Object value);

  String getTooltipFor(Object value);

  Icon getIconFor(Object value);

  boolean hasSeparatorAboveOf(Object value);

  String getCaptionAboveOf(Object value);
}
