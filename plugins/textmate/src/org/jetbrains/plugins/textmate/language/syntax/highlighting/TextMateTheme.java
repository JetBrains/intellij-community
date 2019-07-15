package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtils;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;
import static org.jetbrains.plugins.textmate.plist.Plist.EMPTY_PLIST;

public class TextMateTheme {
  public static final TextMateDefaultColorsProvider DEFAULT_COLORS_PROVIDER = new TextMateDefaultColorsProvider();
  public static final String DEFAULT_ATTRIBUTES_NAME = "textmate.default";
  public static final TextMateTheme EMPTY_THEME =
    new TextMateTheme("Unnamed", new HashMap<>(), TextAttributes.ERASE_MARKER);

  private final String name;
  private final Map<CharSequence, TextAttributes> myTextAttributes;
  @NotNull
  private final TextAttributes myDefaultAttributes;

  protected TextMateTheme(String name, Map<CharSequence, TextAttributes> textAttributes, @NotNull TextAttributes defaultAttributes) {
    this.name = name;
    myTextAttributes = initWithDefaultColors(textAttributes);
    myDefaultAttributes = defaultAttributes;
  }

  @NotNull
  private static Map<CharSequence, TextAttributes> initWithDefaultColors(@NotNull Map<CharSequence, TextAttributes> attributes) {
    for (CharSequence key : DEFAULT_COLORS_PROVIDER.getAllDefaultKeys()) {
      if (!attributes.containsKey(key)) {
        attributes.put(key, DEFAULT_COLORS_PROVIDER.getTextAttributes(key));
      }
    }
    return TextMateEditorUtils.compactMap(attributes);
  }

  public Color getDefaultBackground() {
    return myDefaultAttributes.getBackgroundColor();
  }

  public String getName() {
    return name;
  }

  public Set<CharSequence> getRules() {
    return myTextAttributes.keySet();
  }

  public TextAttributesKey getTextAttributesKey(CharSequence highlightingRule) {
    String key = getName() + "." + highlightingRule;
    return createTextAttributesKey(key, getTextAttributes(highlightingRule));
  }

  private TextAttributes getTextAttributes(CharSequence highlightingRule) {
    return myTextAttributes.getOrDefault(highlightingRule, myDefaultAttributes);
  }

  @NotNull
  public static TextMateTheme load(Plist plist, @NotNull Interner<CharSequence> interner) {
    String themeName = plist.getPlistValue(Constants.NAME_KEY, "Unnamed").getString();
    Map<CharSequence, TextAttributes> attributes = new HashMap<>();
    final PListValue settings = plist.getPlistValue(Constants.SETTINGS_KEY);
    if (settings == null) {
      return EMPTY_THEME;
    }
    TextAttributes defaultAttributes = new TextAttributes();
    for (PListValue colorDefinition : settings.getArray()) {
      final Plist colorDefinitionPlist = colorDefinition.getPlist();
      String scope = colorDefinitionPlist.getPlistValue(Constants.SCOPE_KEY, "").getString();
      final Plist colorRuleSettings = colorDefinitionPlist.getPlistValue(Constants.SETTINGS_KEY, EMPTY_PLIST).getPlist();
      if (scope.isEmpty()) {
        PreferencesReadUtil.fillTextAttributes(defaultAttributes, colorRuleSettings, defaultAttributes.getBackgroundColor());
      }
      else {
        final TextAttributes textAttributes = new TextAttributes();
        PreferencesReadUtil.fillTextAttributes(textAttributes, colorRuleSettings, defaultAttributes.getBackgroundColor());
        attributes.put(interner.intern(scope), textAttributes);
      }
    }
    return new TextMateTheme(themeName, attributes, defaultAttributes);
  }
}
