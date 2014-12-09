/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.tasks.trello.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Mikhail Golubev
 */
public class TrelloLabel {
  public enum LabelColor {

    GREEN(new Color(0x34b27d)),
    YELLOW(new Color(0xdbdb57)),
    ORANGE(new Color(0xE09952)),
    RED(new Color(0xcb4d4d)),
    PURPLE(new Color(0x9933cc)),
    BLUE(new Color(0x4d77cb)),
    SKY(new Color(0x33cee6)),
    LIME(new Color(0x45e660)),
    PINK(new Color(0xff78cb)),
    BLACK(new Color(0x4d4d4d)),
    NO_COLOR(null);

    private final Color color;

    LabelColor(@Nullable Color c) {
      this.color = c;
    }

    public Color getColor() {
      return color;
    }
  }

  private String name;

  private LabelColor color;

  @NotNull
  public String getName() {
    return name;
  }

  @Nullable
  public LabelColor getColor() {
    return color;
  }
}
