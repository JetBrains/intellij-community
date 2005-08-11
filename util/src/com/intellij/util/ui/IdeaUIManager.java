/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;


@SuppressWarnings({"HardCodedStringLiteral"})
public class IdeaUIManager {

  public static Color getTableSelectionBackgroung(){
    return UIManager.getColor("Table.selectionBackground");
  }

  public static Color getTableBackgroung(){
    return UIManager.getColor("Table.Backgroung");
  }

  public static Color getTableSelectionForegroung(){
    return UIManager.getColor("Table.selectionForeground");
  }

  public static Color getTableForegroung(){
    return UIManager.getColor("Table.Foregroung");
  }

  public static Color getTreeForegroung() {
    return UIManager.getColor("Tree.Foregroung");
  }

}
