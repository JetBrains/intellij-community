/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.dnd;

import javax.swing.*;
import java.awt.*;

interface DropTargetHighlighter {

  void show(JLayeredPane aPane, Rectangle aRectangle, DnDEvent aEvent);

  void vanish();

  int getMask();

}
