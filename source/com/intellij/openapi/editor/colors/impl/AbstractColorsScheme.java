/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import gnu.trove.THashMap;
import org.jdom.Element;
import com.intellij.util.containers.HashMap;
import com.intellij.codeInsight.CodeInsightColors;

import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class AbstractColorsScheme implements EditorColorsScheme {
  protected  EditorColorsScheme myParentScheme;

  protected int myEditorFontSize;
  protected float myLineSpacing;

  private Map<EditorFontType, Font> myFonts = new THashMap<EditorFontType, Font>();
  private String myEditorFontName;
  private String mySchemeName;

  // version influences XML format and triggers migration
  private int myVersion;

  protected Map<ColorKey, Color> myColorsMap = new HashMap<ColorKey, Color>();
  protected Map<TextAttributesKey, TextAttributes> myAttributesMap = new HashMap<TextAttributesKey, TextAttributes>();

  private static final String DEFAULT_FONT_NAME = "Courier";
  protected static final String EDITOR_FONT_NAME = "EDITOR_FONT_NAME";
  protected static final String SCHEME_NAME = "SCHEME_NAME";
  protected DefaultColorSchemesManager myDefaultColorSchemesManager;
  private Color myDeprecatedBackgroundColor = null;

  protected AbstractColorsScheme(EditorColorsScheme parentScheme, DefaultColorSchemesManager defaultColorSchemesManager) {
    myParentScheme = parentScheme;
    myDefaultColorSchemesManager = defaultColorSchemesManager;
  }

  public AbstractColorsScheme(DefaultColorSchemesManager defaultColorSchemesManager) {
    myDefaultColorSchemesManager = defaultColorSchemesManager;
  }

  public abstract void setAttributes(TextAttributesKey key, TextAttributes attributes);
  public abstract TextAttributes getAttributes(TextAttributesKey key);

  public abstract void setColor(ColorKey key, Color color);
  public abstract Color getColor(ColorKey key);

  public Color getDefaultBackground() {
    final Color c = getAttributes(HighlighterColors.TEXT).getBackgroundColor();
    return c != null ? c : Color.white;
  }

  public Color getDefaultForeground() {
    final Color c = getAttributes(HighlighterColors.TEXT).getForegroundColor();
    return c != null ? c : Color.black;
  }

  public String getName() {
    return mySchemeName;
  }

  public void setFont(EditorFontType key, Font font) {
    myFonts.put(key, font);
  }

  public abstract Object clone();

  public void copyTo(AbstractColorsScheme newScheme) {
    newScheme.myEditorFontSize = myEditorFontSize;
    newScheme.myLineSpacing = myLineSpacing;
    newScheme.setEditorFontName(getEditorFontName());

    final Set<EditorFontType> types = myFonts.keySet();
    for (EditorFontType type : types) {
      Font font = myFonts.get(type);
      newScheme.setFont(type, font);
    }

    newScheme.myAttributesMap = new HashMap<TextAttributesKey, TextAttributes>(myAttributesMap);
    newScheme.myColorsMap = new HashMap<ColorKey, Color>(myColorsMap);
    newScheme.myVersion = myVersion;
  }

  public void setEditorFontName(String fontName) {
    myEditorFontName = fontName;
    initFonts();
  }

  public void setEditorFontSize(int fontSize) {
    myEditorFontSize = fontSize;
    initFonts();
  }

  public void setLineSpacing(float lineSpacing) {
    myLineSpacing = lineSpacing;
  }

  public Font getFont(EditorFontType key) {
    return myFonts.get(key);
  }

  public void setName(String name) {
    mySchemeName = name;
  }

  public String getEditorFontName() {
    return myEditorFontName == null ? AbstractColorsScheme.DEFAULT_FONT_NAME : myEditorFontName;
  }

  public int getEditorFontSize() {
    return myEditorFontSize;
  }

  public float getLineSpacing() {
    return myLineSpacing <= 0?1f:myLineSpacing;
  }

  protected void initFonts() {
    String editorFontName = getEditorFontName();
    int editorFontSize = getEditorFontSize();

    Font plainFont = new Font(editorFontName, Font.PLAIN, editorFontSize);
    Font boldFont = new Font(editorFontName, Font.BOLD, editorFontSize);
    Font italicFont = new Font(editorFontName, Font.ITALIC, editorFontSize);
    Font boldItalicFont = new Font(editorFontName, Font.BOLD + Font.ITALIC, editorFontSize);

    myFonts.put(EditorFontType.PLAIN, plainFont);
    myFonts.put(EditorFontType.BOLD, boldFont);
    myFonts.put(EditorFontType.ITALIC, italicFont);
    myFonts.put(EditorFontType.BOLD_ITALIC, boldItalicFont);
  }

  public String toString() {
    return getName();
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    if ("scheme".equals(parentNode.getName())) {
      readScheme(parentNode);
    } else {
      for (final Object o : parentNode.getChildren("scheme")) {
        Element element = (Element)o;
        readScheme(element);
      }
    }
    initFonts();
  }

  private void readScheme(Element node) throws InvalidDataException {
    myDeprecatedBackgroundColor = null;
    if ("scheme".equals(node.getName())) {
      setName(node.getAttributeValue("name"));
      myVersion = Integer.parseInt(node.getAttributeValue("version", "0"));
      String isDefaultScheme = node.getAttributeValue("default_scheme");
      if (isDefaultScheme == null || "false".equals(isDefaultScheme)) {
        String parentSchemeName = node.getAttributeValue("parent_scheme");
        if (parentSchemeName == null) parentSchemeName = "Default";
        myParentScheme = myDefaultColorSchemesManager.getScheme(parentSchemeName);
      }

      for (final Object o : node.getChildren()) {
        Element childNode = (Element)o;
        if ("option".equals(childNode.getName())) {
          readSettings(childNode);
        }
        else if ("colors".equals(childNode.getName())) {
          readColors(childNode);
        }
        else if ("attributes".equals(childNode.getName())) {
          readAttributes(childNode);
        }
      }

      if (myDeprecatedBackgroundColor != null) {
        TextAttributes textAttributes = myAttributesMap.get(HighlighterColors.TEXT);
        if (textAttributes == null) {
          textAttributes = new TextAttributes(Color.black, myDeprecatedBackgroundColor, null, EffectType.BOXED, Font.PLAIN);
          myAttributesMap.put(HighlighterColors.TEXT, textAttributes);
        }
        else {
          textAttributes.setBackgroundColor(myDeprecatedBackgroundColor);
        }
      }

      initFonts();
    }
  }

  private void readAttributes(Element childNode) throws InvalidDataException {
    for (final Object o : childNode.getChildren("option")) {
      Element e = (Element)o;
      TextAttributesKey name = TextAttributesKey.find(e.getAttributeValue("name"));
      TextAttributes attr = new TextAttributes();
      Element value = e.getChild("value");
      attr.readExternal(value);
      myAttributesMap.put(name, attr);
      migrateErrorStripeColorFrom45(name, attr);
    }
  }

  private void migrateErrorStripeColorFrom45(final TextAttributesKey name, final TextAttributes attr) {
    if (myVersion != 0) return;
    Color defaultColor = DEFAULT_ERROR_STRIPE_COLOR.get(name.getExternalName());
    if (defaultColor != null && attr.getErrorStripeColor() == null) {
      attr.setErrorStripeColor(defaultColor);
    }
  }
  private static final Map<String, Color> DEFAULT_ERROR_STRIPE_COLOR = new THashMap<String, Color>();
  static {
    DEFAULT_ERROR_STRIPE_COLOR.put(CodeInsightColors.ERRORS_ATTRIBUTES.getExternalName(), Color.red);
    DEFAULT_ERROR_STRIPE_COLOR.put(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES.getExternalName(), Color.red);
    DEFAULT_ERROR_STRIPE_COLOR.put(CodeInsightColors.WARNINGS_ATTRIBUTES.getExternalName(), Color.yellow);
    DEFAULT_ERROR_STRIPE_COLOR.put(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.getExternalName(), Color.yellow);
    DEFAULT_ERROR_STRIPE_COLOR.put(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.getExternalName(), Color.yellow);
    DEFAULT_ERROR_STRIPE_COLOR.put(CodeInsightColors.DEPRECATED_ATTRIBUTES.getExternalName(), Color.yellow);
  }

  private void readColors(Element childNode) {
    for (final Object o : childNode.getChildren("option")) {
      Element colorElement = (Element)o;
      Color valueColor = readColorValue(colorElement);
      final String colorName = colorElement.getAttributeValue("name");
      if ("BACKGROUND".equals(colorName)) {
        // This setting has been deprecated to usages of HighlighterColors.TEXT attributes.
        myDeprecatedBackgroundColor = valueColor;
      }

      ColorKey name = ColorKey.find(colorName);
      myColorsMap.put(name, valueColor);
    }
  }

  private static Color readColorValue(final Element colorElement) {
    String value = colorElement.getAttributeValue("value");
    Color valueColor = null;
    if (value != null && value.trim().length() > 0) {
      try {
        valueColor = new Color(Integer.parseInt(value, 16));
      } catch (NumberFormatException e) {
      }
    }
    return valueColor;
  }

  private void readSettings(Element childNode) {
    String name = childNode.getAttributeValue("name");
    String value = childNode.getAttributeValue("value");
    if ("LINE_SPACING".equals(name)) {
      myLineSpacing = Float.parseFloat(value);
    }
    else if ("EDITOR_FONT_SIZE".equals(name)) {
      myEditorFontSize = Integer.parseInt(value);
    }
    else if ("EDITOR_FONT_NAME".equals(name)) {
      setEditorFontName(value);
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    parentNode.setAttribute("name", getName());
    parentNode.setAttribute("version", Integer.toString(myVersion));

    if (myParentScheme != null) {
      parentNode.setAttribute("parent_scheme", myParentScheme.getName());
    }

    Element element = new Element("option");
    element.setAttribute("name", "LINE_SPACING");
    element.setAttribute("value", String.valueOf(getLineSpacing()));
    parentNode.addContent(element);

    element = new Element("option");
    element.setAttribute("name", "EDITOR_FONT_SIZE");
    element.setAttribute("value", String.valueOf(getEditorFontSize()));
    parentNode.addContent(element);

    element = new Element("option");
    element.setAttribute("name", "EDITOR_FONT_NAME");
    element.setAttribute("value", getEditorFontName());
    parentNode.addContent(element);

    Element colorElements = new Element("colors");
    Element attrElements = new Element("attributes");

    writeColors(colorElements);
    writeAttributes(attrElements);

    parentNode.addContent(colorElements);
    parentNode.addContent(attrElements);
  }

  private boolean haveToWrite(final TextAttributesKey key, final TextAttributes value, final TextAttributes defaultAttribute) {
    boolean hasDefaultValue = value.equals(defaultAttribute);
    if (myParentScheme == null) return !hasDefaultValue;
    if (EditorColorsManager.getInstance().getGlobalScheme() == this
        && myParentScheme instanceof AbstractColorsScheme
        && !((AbstractColorsScheme)myParentScheme).myAttributesMap.containsKey(key)) return true;
    return !value.equals(myParentScheme.getAttributes(key));
  }

  private void writeAttributes(Element attrElements) throws WriteExternalException {
    Element element;
    List<TextAttributesKey> list = new ArrayList<TextAttributesKey>(myAttributesMap.keySet());
    Collections.sort(list);

    TextAttributes defaultAttr = new TextAttributes();
    for (TextAttributesKey key: list) {
      TextAttributes value = myAttributesMap.get(key);
      if (!haveToWrite(key,value,defaultAttr)) continue;
      element = new Element("option");
      element.setAttribute("name", key.getExternalName());
      Element valueElement = new Element("value");
      value.writeExternal(valueElement);
      element.addContent(valueElement);
      attrElements.addContent(element);
    }
  }

  private void writeColors(Element colorElements) {
    List<ColorKey> list = new ArrayList<ColorKey>(myColorsMap.keySet());
    Collections.sort(list);

    for (ColorKey key : list) {
      Color value = myColorsMap.get(key);
      if (myParentScheme != null) {
        if (Comparing.equal(myParentScheme.getColor(key), value)) {
          continue;
        }
      }
      Element element = new Element("option");
      element.setAttribute("name", key.getExternalName());
      element.setAttribute("value", value != null ? Integer.toString(value.getRGB() & 0xFFFFFF, 16) : "");
      colorElements.addContent(element);
    }
  }
}
