package com.intellij.ide;

import java.awt.*;

public interface IdePopup {

  Component getComponent();
  boolean dispatch(AWTEvent event);

}
