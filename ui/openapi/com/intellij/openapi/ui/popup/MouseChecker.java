/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import java.awt.event.MouseEvent;

public interface MouseChecker {

  boolean check(MouseEvent event);

}
