/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.projectRoots.SdkModel;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2004
 */
public interface NotifiableSdkModel extends SdkModel{
  Listener getMulticaster();
}
