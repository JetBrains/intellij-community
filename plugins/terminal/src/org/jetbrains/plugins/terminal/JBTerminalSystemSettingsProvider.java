package org.jetbrains.plugins.terminal;

import com.intellij.application.options.OptionsConstants;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
class JBTerminalSystemSettingsProvider extends DefaultSettingsProvider {

  private final EditorColorsScheme myColorScheme;

  JBTerminalSystemSettingsProvider() {
    myColorScheme = getColorScheme();
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
  public ColorPalette getTerminalColorPalette() {
    return SystemInfo.isWindows ? ColorPalette.WINDOWS_PALETTE : ColorPalette.XTERM_PALETTE;
  }

  private KeyStroke[] getKeyStrokesByActionId(String actionId) {
    List<KeyStroke> keyStrokes = new ArrayList<KeyStroke>();
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
    return ttyConnector instanceof PtyProcessTtyConnector; //close tab only on logout of local pty, not remote
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

    for (String font : fonts) {
      if (isApplicable(font)) {
        return font;
      }
    }
    return "Monospaced-14";
  }

  private static boolean isApplicable(String font) {
    if ("Source Code Pro".equals(font)) {
      return false;
    }
    return true;
  }

  @Override
  public float getTerminalFontSize() {
    return (float)myColorScheme.getConsoleFontSize();
  }


  @Override
  public boolean useAntialiasing() {
    return UISettings.getInstance().ANTIALIASING_IN_EDITOR;
  }

  @Override
  public int caretBlinkingMs() {
    return EditorSettingsExternalizable.getInstance().getBlinkPeriod();
  }
  
  public EditorColorsScheme getColorScheme() {
    return createBoundColorSchemeDelegate(null);
  }

  @NotNull
  public EditorColorsScheme createBoundColorSchemeDelegate(@Nullable final EditorColorsScheme customGlobalScheme) {
    return new MyColorSchemeDelegate(customGlobalScheme);
  }

  private static class MyColorSchemeDelegate implements EditorColorsScheme {

    private final FontPreferences myFontPreferences = new FontPreferences();
    private final HashMap<TextAttributesKey, TextAttributes> myOwnAttributes = new HashMap<TextAttributesKey, TextAttributes>();
    private final HashMap<ColorKey, Color> myOwnColors = new HashMap<ColorKey, Color>();
    private final EditorColorsScheme myCustomGlobalScheme;
    private Map<EditorFontType, Font> myFontsMap = null;
    private int myMaxFontSize = OptionsConstants.MAX_EDITOR_FONT_SIZE;
    private int myFontSize = -1;
    private String myFaceName = null;
    private EditorColorsScheme myGlobalScheme;

    private MyColorSchemeDelegate(@Nullable final EditorColorsScheme globalScheme) {
      myCustomGlobalScheme = globalScheme;
      updateGlobalScheme();
    }

    private EditorColorsScheme getGlobal() {
      return myGlobalScheme;
    }

    @Override
    public String getName() {
      return getGlobal().getName();
    }


    protected void initFonts() {
      String editorFontName = getEditorFontName();
      int editorFontSize = getEditorFontSize();
      myFontPreferences.clear();
      myFontPreferences.register(editorFontName, editorFontSize);

      myFontsMap = new EnumMap<EditorFontType, Font>(EditorFontType.class);

      Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
      Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
      Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
      Font boldItalicFont = new Font(editorFontName, Font.BOLD | Font.ITALIC, editorFontSize);

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
      return getGlobal().getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
    }

    @NotNull
    @Override
    public Color getDefaultForeground() {
      return getGlobal().getDefaultForeground();
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
      return myFontPreferences;
    }

    @Override
    public void setFontPreferences(@NotNull FontPreferences preferences) {
      preferences.copyTo(myFontPreferences);
      initFonts();
    }

    @Override
    public int getEditorFontSize() {
      if (myFontSize == -1) {
        return getGlobal().getEditorFontSize();
      }
      return myFontSize;
    }

    @Override
    public void setEditorFontSize(int fontSize) {
      if (fontSize < 8) fontSize = 8;
      if (fontSize > myMaxFontSize) fontSize = myMaxFontSize;
      myFontSize = fontSize;
      initFonts();
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
      if (myFaceName == null) {
        return getGlobal().getEditorFontName();
      }
      return myFaceName;
    }

    @Override
    public void setEditorFontName(String fontName) {
      myFaceName = fontName;
      initFonts();
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
    public void readExternal(Element element) throws InvalidDataException {
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
    }

    public void updateGlobalScheme() {
      myGlobalScheme = myCustomGlobalScheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : myCustomGlobalScheme;
      int globalFontSize = getGlobal().getEditorFontSize();
      myMaxFontSize = Math.max(OptionsConstants.MAX_EDITOR_FONT_SIZE, globalFontSize);
    }

    @NotNull
    @Override
    public FontPreferences getConsoleFontPreferences() {
      return getGlobal().getConsoleFontPreferences();
    }

    @Override
    public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
      getGlobal().setConsoleFontPreferences(preferences);
    }

    @Override
    public String getConsoleFontName() {
      return getGlobal().getConsoleFontName();
    }

    @Override
    public void setConsoleFontName(String fontName) {
      getGlobal().setConsoleFontName(fontName);
    }

    @Override
    public int getConsoleFontSize() {
      return getGlobal().getConsoleFontSize();
    }

    @Override
    public void setConsoleFontSize(int fontSize) {
      getGlobal().setConsoleFontSize(fontSize);
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
}
