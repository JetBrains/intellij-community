/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.ui;

import java.util.EventListener;

public interface UISettingsListener extends EventListener{
  void uiSettingsChanged(UISettings source);
}
