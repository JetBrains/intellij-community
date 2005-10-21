package com.intellij.ui;

import java.util.EventListener;
import java.util.EventObject;

/**
 * @author mike
 */
public interface HintListener extends EventListener{
  void hintHidden(EventObject event);
}
