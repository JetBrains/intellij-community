/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.pom;

import javax.swing.*;

public interface PomPresentation {
  /**
   * @return object name to be presented in most renderers across the program.
   */
  String getName();

  /**
   * @return location info to be used by some renderers to present additional info on item's location. Usually displayed as grayed text next to item name (like class's package)
   */
  String getLocationString();

  /**
   * @param open only meaningful when used in a tree renderers. false is passed when icon for other renderers is required.
   * @return icon
   */
  Icon getIcon(boolean open);

  /**
   * Some structures (project view) are to sort entities by their type meaning subdirectories should go prior files in the directory.
   * This weight value is used to perform sorting.
   * @return type weight value. The lesser value is the higher item would be placed in the hierarchy.
   */
  int getWeight();
}