package com.intellij.codeInsight.daemon.impl;

import java.util.EventListener;

public interface EditorTrackerListener extends EventListener{
  void activeEditorsChanged();
}
