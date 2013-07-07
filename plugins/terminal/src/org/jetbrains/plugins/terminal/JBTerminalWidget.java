package org.jetbrains.plugins.terminal;

import com.intellij.application.options.OptionsConstants;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.util.containers.HashMap;
import com.jediterm.terminal.display.BackBuffer;
import com.jediterm.terminal.display.StyleState;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.SystemSettingsProvider;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

public class JBTerminalWidget extends JediTermWidget {

  public JBTerminalWidget(SystemSettingsProvider settingsProvider) {
    super(settingsProvider);
  }

  @Override
  protected JBTerminalPanel createTerminalPanel(@NotNull SystemSettingsProvider settingsProvider,
                                                @NotNull StyleState styleState,
                                                @NotNull BackBuffer backBuffer) {
    return new JBTerminalPanel(settingsProvider, backBuffer, styleState, getColorScheme());
  }

  public EditorColorsScheme getColorScheme() {
    return createBoundColorSchemeDelegate(null);
  }

  @Override
  protected JScrollBar createScrollBar() {
    return new JBScrollBar();
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
      return getGlobal().getDefaultBackground();
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
