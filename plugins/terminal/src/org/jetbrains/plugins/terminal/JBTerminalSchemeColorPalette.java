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
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.ColoredOutputTypeRegistry;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.jediterm.terminal.emulator.ColorPalette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author traff
 */
public class JBTerminalSchemeColorPalette extends ColorPalette {

  private final EditorColorsScheme myColorsScheme;

  protected JBTerminalSchemeColorPalette(EditorColorsScheme scheme) {
    super();
    myColorsScheme = scheme;
  }

  @Override
  public Color[] getIndexColors() {
    Color[] result = XTERM_PALETTE.getIndexColors();
    for (int i = 1; i < 7; i++) {
      result[i] = myColorsScheme.getAttributes(ColoredOutputTypeRegistry.getAnsiColorKey(i)).getForegroundColor();
    }
    return result;
  }
}
