// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ColorSampleLookupValue {
  private static final int NORMAL_PRIORITY = 0;
  private static final int HIGHER_PRIORITY = 1;
  static final int HIGH_PRIORITY = 2;

  private static volatile ColorSampleLookupValue[] ourColors;
  private final boolean myIsStandard;
  private final String myName;
  private final String myValue;
  private Color myColor;

  public ColorSampleLookupValue(String name, String value, boolean isStandard) {
    myName = name;
    myValue = value;
    myIsStandard = isStandard;
  }

  public String getPresentation() {
    return myName != null ? myName : myValue;
  }

  public String getValue() {
    return myValue;
  }

  public boolean isIsStandard() {
    return myIsStandard;
  }

  private @Nullable Icon getIcon() {
    if (myColor == null) {
      if (myValue.startsWith("#")) {
        try {
          myColor = Color.decode("0x" + myValue.substring(1));
        }
        catch (NumberFormatException e) {
          return null;
        }
      }
    }

    if (myColor != null) {
      return ColorIconCache.getIconCache().getIcon(myColor, 16);
    }

    return null;
  }

  public static ColorSampleLookupValue @NotNull [] getColors() {
    if (ourColors == null) {
      synchronized (ColorSampleLookupValue.class) {
        if (ourColors == null) {
          List<ColorSampleLookupValue> colorsList = new LinkedList<>();
          StringTokenizer tokenizer = new StringTokenizer(ColorMap.systemColorsString, "\n");

          while (tokenizer.hasMoreTokens()) {
            String name = tokenizer.nextToken();
            colorsList.add(new ColorSampleLookupValue(name, name, false));
            tokenizer.nextToken();
          }

          tokenizer = new StringTokenizer(ColorMap.standardColorsString, ", \n");
          HashMap<String, String> standardColors = new HashMap<>();

          while (tokenizer.hasMoreTokens()) {
            String name = tokenizer.nextToken();
            String value = tokenizer.nextToken();
            standardColors.put(name, name);

            colorsList.add(new ColorSampleLookupValue(name, value, true));
          }

          tokenizer = new StringTokenizer(ColorMap.colorsString, " \t\n");

          while (tokenizer.hasMoreTokens()) {
            String name = tokenizer.nextToken();
            String hexValue = tokenizer.nextToken();

            tokenizer.nextToken(); // skip rgb

            if (!standardColors.containsKey(name)) {
              colorsList.add(new ColorSampleLookupValue(name, hexValue, false));
            }
          }

          ourColors = colorsList.toArray(new ColorSampleLookupValue[0]);
        }
      }
    }
    return ourColors;
  }

  public @Nullable String getTypeHint() {
    return myName != null && !StringUtil.startsWithChar(myName, '#')
           && myValue != null && StringUtil.startsWithChar(myValue, '#') ? myValue : null;
  }

  public String getName() {
    return myName;
  }

  public int getPriority() {
    return myName == null || Character.isLowerCase(myName.charAt(0)) ? HIGHER_PRIORITY : NORMAL_PRIORITY;
  }

  @Override
  public String toString() {
    return myName == null ? myValue : myValue + " " + myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ColorSampleLookupValue value)) {
      return false;
    }

    if (myIsStandard != value.myIsStandard) {
      return false;
    }
    if (!Objects.equals(myColor, value.myColor)) {
      return false;
    }
    if (!Objects.equals(myName, value.myName)) {
      return false;
    }
    if (!Objects.equals(myValue, value.myValue)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myIsStandard ? 1 : 0);
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myValue != null ? myValue.hashCode() : 0);
    result = 31 * result + (myColor != null ? myColor.hashCode() : 0);
    return result;
  }

  public @NotNull LookupElement toLookupElement() {
    LookupElementBuilder lookupElement = LookupElementBuilder.create(this, getPresentation())
      .withTypeText(getTypeHint())
      .withIcon(getIcon());
    if (!isIsStandard()) {
      lookupElement = lookupElement.withInsertHandler(
        (context, item) -> context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getValue()));
    }
    return PrioritizedLookupElement.withPriority(lookupElement, getPriority());
  }
}
