/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Sets;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.HashMap;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.settings.DefaultTabbedSettingsProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author traff
 */
public class JBTerminalSystemSettingsProvider extends DefaultTabbedSettingsProvider implements Disposable {
  private Set<TerminalSettingsListener> myListeners = Sets.newHashSet();

  private final MyColorSchemeDelegate myColorScheme;

  public JBTerminalSystemSettingsProvider() {
    myColorScheme = createBoundColorSchemeDelegate(null);

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        int size = consoleFontSize(JBTerminalSystemSettingsProvider.this.myColorScheme);

        if (myColorScheme.getConsoleFontSize() != size) {
          myColorScheme.setConsoleFontSize(size);
          fireFontChanged();
        }
      }
    }, this);

    EditorColorsManager.getInstance().addEditorColorsListener(new EditorColorsAdapter() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        myColorScheme.updateGlobalScheme(scheme);
        fireFontChanged();
      }
    }, this);
  }

  private static int consoleFontSize(MyColorSchemeDelegate colorScheme) {
    int size;
    if (UISettings.getInstance().PRESENTATION_MODE) {
      size = UISettings.getInstance().PRESENTATION_MODE_FONT_SIZE;
    }
    else {
      size = colorScheme.getGlobal().getConsoleFontSize();
    }
    return size;
  }

  @Override
  public KeyStroke[] getCopyKeyStrokes() {
    return getKeyStrokesByActionId("$Copy");
  }

  @Override
  public KeyStroke[] getPasteKeyStrokes() {
    return getKeyStrokesByActionId("$Paste");
  }

  @Override
  public KeyStroke[] getNextTabKeyStrokes() {
    return getKeyStrokesByActionId("NextTab");
  }

  @Override
  public KeyStroke[] getPreviousTabKeyStrokes() {
    return getKeyStrokesByActionId("PreviousTab");
  }

  @Override
  public KeyStroke[] getFindKeyStrokes() {
    return getKeyStrokesByActionId("Find");
  }

  @Override
  public ColorPalette getTerminalColorPalette() {
    return new JBTerminalSchemeColorPalette(myColorScheme);
  }

  private KeyStroke[] getKeyStrokesByActionId(String actionId) {
    List<KeyStroke> keyStrokes = new ArrayList<>();
    Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    for (Shortcut sc : shortcuts) {
      if (sc instanceof KeyboardShortcut) {
        KeyStroke ks = ((KeyboardShortcut)sc).getFirstKeyStroke();
        keyStrokes.add(ks);
      }
    }

    return keyStrokes.toArray(new KeyStroke[keyStrokes.size()]);
  }

  @Override
  public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
    return TerminalOptionsProvider.getInstance().closeSessionOnLogout();
  }

  @Override
  public String tabName(TtyConnector ttyConnector, String sessionName) { //for local terminal use name from settings
    if (ttyConnector instanceof PtyProcessTtyConnector) {
      return TerminalOptionsProvider.getInstance().getTabName();
    }
    else {
      return sessionName;
    }
  }

  @Override
  public float getLineSpace() {
    return myColorScheme.getConsoleLineSpacing();
  }

  @Override
  public boolean useInverseSelectionColor() {
    return false;
  }

  @Override
  public TextStyle getSelectionColor() {
    return new TextStyle(TerminalColor.awt(myColorScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR)),
                         TerminalColor.awt(myColorScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)));
  }

  @Override
  public TextStyle getFoundPatternColor() {
    return new TextStyle(TerminalColor.awt(myColorScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).getForegroundColor()),
                         TerminalColor.awt(myColorScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES).getBackgroundColor()));
  }

  @Override
  public TextStyle getDefaultStyle() {
    return new TextStyle(TerminalColor.awt(myColorScheme.getDefaultForeground()), TerminalColor.awt(
      myColorScheme.getDefaultBackground()));
  }

  @Override
  public Font getTerminalFont() {
    Font normalFont = Font.decode(getFontName());

    if (normalFont == null) {
      normalFont = super.getTerminalFont();
    }

    normalFont = normalFont.deriveFont(getTerminalFontSize());

    return normalFont;
  }

  public String getFontName() {
    List<String> fonts = myColorScheme.getConsoleFontPreferences().getEffectiveFontFamilies();

    if (fonts.size() > 0) {
      return fonts.get(0);
    }

    return "Monospaced-14";
  }

  @Override
  public float getTerminalFontSize() {
    return (float)myColorScheme.getConsoleFontSize();
  }


  @Override
  public boolean useAntialiasing() {
    return true; // we return true here because all the settings are checked again in UiSettings.setupAntialiasing
  }

  @Override
  public int caretBlinkingMs() {
    if (!EditorSettingsExternalizable.getInstance().isBlinkCaret()) {
      return 0;
    }
    return EditorSettingsExternalizable.getInstance().getBlinkPeriod();
  }

  @Override
  public int getBufferMaxLinesCount() {
    final int linesCount = Registry.get("terminal.buffer.max.lines.count").asInteger();
    if (linesCount > 0) {
      return linesCount;
    }
    else {
      return super.getBufferMaxLinesCount();
    }
  }

  public EditorColorsScheme getColorScheme() {
    return myColorScheme;
  }

  @Override
  public boolean audibleBell() {
    return TerminalOptionsProvider.getInstance().audibleBell();
  }

  @Override
  public boolean enableMouseReporting() {
    return TerminalOptionsProvider.getInstance().enableMouseReporting();
  }

  @Override
  public boolean copyOnSelect() {
    return TerminalOptionsProvider.getInstance().copyOnSelection();
  }

  @Override
  public boolean pasteOnMiddleMouseClick() {
    return TerminalOptionsProvider.getInstance().pasteOnMiddleMouseButton();
  }

  @NotNull
  private static MyColorSchemeDelegate createBoundColorSchemeDelegate(@Nullable final EditorColorsScheme customGlobalScheme) {
    return new MyColorSchemeDelegate(customGlobalScheme);
  }

  @Override
  public boolean forceActionOnMouseReporting() {
    return true;
  }

  @Override
  public void dispose() {

  }

  private static class MyColorSchemeDelegate implements EditorColorsScheme {

    private final FontPreferences myFontPreferences = new FontPreferences();
    private final HashMap<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<>();
    private final HashMap<ColorKey, Color> myOwnColors = new HashMap<>();
    private Map<EditorFontType, Font> myFontsMap = null;
    private String myFaceName = null;
    private EditorColorsScheme myGlobalScheme;

    private int myConsoleFontSize;

    private MyColorSchemeDelegate(@Nullable final EditorColorsScheme globalScheme) {
      updateGlobalScheme(globalScheme);
      myConsoleFontSize = consoleFontSize(this);
      initFonts();
    }

    private EditorColorsScheme getGlobal() {
      return myGlobalScheme;
    }

    @NotNull
    @Override
    public String getName() {
      return getGlobal().getName();
    }


    protected void initFonts() {
      String consoleFontName = getConsoleFontName();
      int consoleFontSize = getConsoleFontSize();
      myFontPreferences.clear();
      myFontPreferences.register(consoleFontName, consoleFontSize);

      myFontsMap = new EnumMap<>(EditorFontType.class);

      Font plainFont = new Font(consoleFontName, Font.PLAIN, consoleFontSize);
      Font boldFont = new Font(consoleFontName, Font.BOLD, consoleFontSize);
      Font italicFont = new Font(consoleFontName, Font.ITALIC, consoleFontSize);
      Font boldItalicFont = new Font(consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize);

      myFontsMap.put(EditorFontType.PLAIN, plainFont);
      myFontsMap.put(EditorFontType.BOLD, boldFont);
      myFontsMap.put(EditorFontType.ITALIC, italicFont);
      myFontsMap.put(EditorFontType.BOLD_ITALIC, boldItalicFont);
    }

    @Override
    public void setName(String name) {
      getGlobal().setName(name);
    }

    @Override
    public TextAttributes getAttributes(TextAttributesKey key) {
      if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
      return getGlobal().getAttributes(key);
    }

    @Override
    public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
      myOwnAttributes.put(key, attributes);
    }

    @NotNull
    @Override
    public Color getDefaultBackground() {
      Color color = getGlobal().getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
      return color != null ? color : getGlobal().getDefaultBackground();
    }

    @NotNull
    @Override
    public Color getDefaultForeground() {
      Color foregroundColor = getGlobal().getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).getForegroundColor();
      return foregroundColor != null ? foregroundColor : getGlobal().getDefaultForeground();
    }

    @Override
    public Color getColor(ColorKey key) {
      if (myOwnColors.containsKey(key)) return myOwnColors.get(key);
      return getGlobal().getColor(key);
    }

    @Override
    public void setColor(ColorKey key, Color color) {
      myOwnColors.put(key, color);
    }

    @NotNull
    @Override
    public FontPreferences getFontPreferences() {
      return myGlobalScheme.getFontPreferences();
    }

    @Override
    public void setFontPreferences(@NotNull FontPreferences preferences) {
      throw new IllegalStateException();
    }

    @Override
    public int getEditorFontSize() {
      return getGlobal().getEditorFontSize();
    }

    @Override
    public void setEditorFontSize(int fontSize) {

    }

    @Override
    public FontSize getQuickDocFontSize() {
      return myGlobalScheme.getQuickDocFontSize();
    }

    @Override
    public void setQuickDocFontSize(@NotNull FontSize fontSize) {
      myGlobalScheme.setQuickDocFontSize(fontSize);
    }

    @Override
    public String getEditorFontName() {
      return getGlobal().getEditorFontName();
    }

    @Override
    public void setEditorFontName(String fontName) {
      throw new IllegalStateException();
    }

    @Override
    public Font getFont(EditorFontType key) {
      if (myFontsMap != null) {
        Font font = myFontsMap.get(key);
        if (font != null) return font;
      }
      return getGlobal().getFont(key);
    }

    @Override
    public void setFont(EditorFontType key, Font font) {
      if (myFontsMap == null) {
        initFonts();
      }
      myFontsMap.put(key, font);
    }

    @Override
    public float getLineSpacing() {
      return getGlobal().getLineSpacing();
    }

    @Override
    public void setLineSpacing(float lineSpacing) {
      getGlobal().setLineSpacing(lineSpacing);
    }

    @Override
    @Nullable
    public Object clone() {
      return null;
    }

    @Override
    public void readExternal(Element element) {
    }

    public void updateGlobalScheme(EditorColorsScheme scheme) {
      myFontsMap = null;
      myGlobalScheme = scheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : scheme;
    }

    @NotNull
    @Override
    public FontPreferences getConsoleFontPreferences() {
      return myFontPreferences;
    }

    @Override
    public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
      preferences.copyTo(myFontPreferences);
      initFonts();
    }

    @Override
    public String getConsoleFontName() {
      if (myFaceName == null) {
        return getGlobal().getConsoleFontName();
      }
      else {
        return myFaceName;
      }
    }

    @Override
    public void setConsoleFontName(String fontName) {
      myFaceName = fontName;
      initFonts();
    }

    @Override
    public int getConsoleFontSize() {
      if (myConsoleFontSize == -1) {
        return getGlobal().getConsoleFontSize();
      }
      else {
        return myConsoleFontSize;
      }
    }

    @Override
    public void setConsoleFontSize(int fontSize) {
      myConsoleFontSize = fontSize;
      initFonts();
    }

    @Override
    public float getConsoleLineSpacing() {
      return getGlobal().getConsoleLineSpacing();
    }

    @Override
    public void setConsoleLineSpacing(float lineSpacing) {
      getGlobal().setConsoleLineSpacing(lineSpacing);
    }
  }

  public void addListener(TerminalSettingsListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(TerminalSettingsListener listener) {
    myListeners.remove(listener);
  }

  public void fireFontChanged() {
    for (TerminalSettingsListener l : myListeners) {
      l.fontChanged();
    }
  }
}
