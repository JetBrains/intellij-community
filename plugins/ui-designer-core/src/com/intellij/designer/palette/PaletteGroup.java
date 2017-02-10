/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.palette;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PaletteGroup {
  private final String myName;
  protected final List<PaletteItem> myItems = new ArrayList<>();

  public PaletteGroup(String name) {
    myName = name;
  }

  public void addItem(PaletteItem item) {
    myItems.add(item);
  }

  public List<PaletteItem> getItems() {
    return myItems;
  }

  public String getName() {
    return myName;
  }
}