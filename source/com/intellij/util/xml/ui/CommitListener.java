/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import java.util.EventListener;

/**
 * @author peter
 */
public interface CommitListener extends EventListener {
  void beforeCommit(DomUIControl control);
  void afterCommit(DomUIControl control);
}
