package com.intellij.openapi.ui.popup;

import java.awt.*;

public interface MaskProvider {

  Shape getMask(Dimension size);

}
