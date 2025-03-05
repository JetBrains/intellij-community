// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.trello.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * This is a stub definition intended to be used with Google GSON. Its fields are initialized reflectively.
 */
@SuppressWarnings("unused")
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

  public @NotNull String getName() {
    return name;
  }

  public @Nullable LabelColor getColor() {
    return color;
  }
}
