package com.intellij.openapi.ui.popup;

import java.awt.*;

public interface IdePopup {

  Component getComponent();
  boolean dispatch(AWTEvent event);

}
