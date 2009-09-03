package com.intellij.uiDesigner;

import com.intellij.uiDesigner.core.Spacer;

import java.awt.*;

/**
 * Used in design time only. 
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
abstract class DesignSpacer extends Spacer{
  protected static final int HANDLE_ATOM_WIDTH = 5;
  protected static final int HANDLE_ATOM_HEIGHT = 3;
  protected static final int HANDLE_ATOM_SPACE = 1;

  protected static final int SPRING_PRERIOD = 4;

  protected static final Color ourColor1 = new Color(8,8,108);
  protected static final Color ourColor2 = new Color(3,26,142);
  protected static final Color ourColor3 = Color.BLACK;
}
