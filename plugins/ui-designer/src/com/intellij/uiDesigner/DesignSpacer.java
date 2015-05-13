/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
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

  protected static final Color ourColor1 = new JBColor(new Color(8,8,108), Gray._168);
  protected static final Color ourColor2 = new JBColor(new Color(3, 26, 142), Gray._128);
  protected static final Color ourColor3 = new JBColor(Gray._0, Gray._128);
}
