/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ColorMap {
  private static final Map<String, String> ourColorNameToHexCodeMap = new HashMap<>(25);
  private static final Map<String, String> ourHexCodeToColorNameMap = new HashMap<>(25);

  @NonNls static final String systemColorsString = "ActiveBorder\n" +
                                                           "    Active window border.\n" +
                                                           "ActiveCaption\n" +
                                                           "    Active window caption.\n" +
                                                           "AppWorkspace\n" +
                                                           "    Background color of multiple document interface.\n" +
                                                           "Background\n" +
                                                           "    Desktop background.\n" +
                                                           "ButtonFace\n" +
                                                           "    Face color for three-dimensional display elements.\n" +
                                                           "ButtonHighlight\n" +
                                                           "    Highlight color for three-dimensional display elements (for edges facing away from the light source).\n" +
                                                           "ButtonShadow\n" +
                                                           "    Shadow color for three-dimensional display elements.\n" +
                                                           "ButtonText\n" +
                                                           "    Text on push buttons.\n" +
                                                           "CaptionText\n" +
                                                           "    Text in caption, size box, and scrollbar arrow box.\n" +
                                                           "GrayText\n" +
                                                           "    Grayed (disabled) text. This color is set to #000 if the current display driver does not support a solid gray color.\n" +
                                                           "Highlight\n" +
                                                           "    Item(s) selected in a control.\n" +
                                                           "HighlightText\n" +
                                                           "    Text of item(s) selected in a control.\n" +
                                                           "InactiveBorder\n" +
                                                           "    Inactive window border.\n" +
                                                           "InactiveCaption\n" +
                                                           "    Inactive window caption.\n" +
                                                           "InactiveCaptionText\n" +
                                                           "    Color of text in an inactive caption.\n" +
                                                           "InfoBackground\n" +
                                                           "    Background color for tooltip controls.\n" +
                                                           "InfoText\n" +
                                                           "    Text color for tooltip controls.\n" +
                                                           "Menu\n" +
                                                           "    Menu background.\n" +
                                                           "MenuText\n" +
                                                           "    Text in menus.\n" +
                                                           "Scrollbar\n" +
                                                           "    Scroll bar gray area.\n" +
                                                           "ThreeDDarkShadow\n" +
                                                           "    Dark shadow for three-dimensional display elements.\n" +
                                                           "ThreeDFace\n" +
                                                           "    Face color for three-dimensional display elements.\n" +
                                                           "ThreeDHighlight\n" +
                                                           "    Highlight color for three-dimensional display elements.\n" +
                                                           "ThreeDLightShadow\n" +
                                                           "    Light color for three-dimensional display elements (for edges facing the light source).\n" +
                                                           "ThreeDShadow\n" +
                                                           "    Dark shadow for three-dimensional display elements.\n" +
                                                           "Window\n" +
                                                           "    Window background.\n" +
                                                           "WindowFrame\n" +
                                                           "    Window frame.\n" +
                                                           "WindowText\n" +
                                                           "    Text in windows. ";
  @NonNls static final String standardColorsString = "maroon #800000 red #ff0000 orange #ffA500 yellow #ffff00 olive #808000\n" +
                                                             "purple #800080 fuchsia #ff00ff white #ffffff lime #00ff00 green #008000\n" +
                                                             "navy #000080 blue #0000ff aqua #00ffff teal #008080\n" +
                                                             "black #000000 silver #c0c0c0 gray #808080";
  @NonNls static final String colorsString = "aliceblue \t#f0f8ff \t240,248,255\n" +
                                                     "  \t  \tantiquewhite \t#faebd7 \t250,235,215\n" +
                                                     "  \t  \taqua \t#00ffff \t0,255,255\n" +
                                                     "  \t  \taquamarine \t#7fffd4 \t127,255,212\n" +
                                                     "  \t  \tazure \t#f0ffff \t240,255,255\n" +
                                                     "  \t  \tbeige \t#f5f5dc \t245,245,220\n" +
                                                     "  \t  \tbisque \t#ffe4c4 \t255,228,196\n" +
                                                     "  \t  \tblack \t#000000 \t0,0,0\n" +
                                                     "  \t  \tblanchedalmond \t#ffebcd \t255,235,205\n" +
                                                     "  \t  \tblue \t#0000ff \t0,0,255\n" +
                                                     "  \t  \tblueviolet \t#8a2be2 \t138,43,226\n" +
                                                     "  \t  \tbrown \t#a52a2a \t165,42,42\n" +
                                                     "  \t  \tburlywood \t#deb887 \t222,184,135\n" +
                                                     "  \t  \tcadetblue \t#5f9ea0 \t95,158,160\n" +
                                                     "  \t  \tchartreuse \t#7fff00 \t127,255,0\n" +
                                                     "  \t  \tchocolate \t#d2691e \t210,105,30\n" +
                                                     "  \t  \tcoral \t#ff7f50 \t255,127,80\n" +
                                                     "  \t  \tcornflowerblue \t#6495ed \t100,149,237\n" +
                                                     "  \t  \tcornsilk \t#fff8dc \t255,248,220\n" +
                                                     "  \t  \tcrimson \t#dc143c \t220,20,60\n" +
                                                     "  \t  \tcyan \t#00ffff \t0,255,255\n" +
                                                     "  \t  \tdarkblue \t#00008b \t0,0,139\n" +
                                                     "  \t  \tdarkcyan \t#008b8b \t0,139,139\n" +
                                                     "  \t  \tdarkgoldenrod \t#b8860b \t184,134,11\n" +
                                                     "  \t  \tdarkgray \t#a9a9a9 \t169,169,169\n" +
                                                     "  \t  \tdarkgrey \t#a9a9a9 \t169,169,169\n" +
                                                     "  \t  \tdarkgreen \t#006400 \t0,100,0\n" +
                                                     "  \t  \tdarkkhaki \t#bdb76b \t189,183,107\n" +
                                                     "  \t  \tdarkmagenta \t#8b008b \t139,0,139\n" +
                                                     "  \t  \tdarkolivegreen \t#556b2f \t85,107,47\n" +
                                                     "  \t  \tdarkorange \t#ff8c00 \t255,140,0\n" +
                                                     "  \t  \tdarkorchid \t#9932cc \t153,50,204\n" +
                                                     "  \t  \tdarkred \t#8b0000 \t139,0,0\n" +
                                                     "  \t  \tdarksalmon \t#e9967a \t233,150,122\n" +
                                                     "  \t  \tdarkseagreen \t#8fbc8f \t143,188,143\n" +
                                                     "  \t  \tdarkslateblue \t#483d8b \t72,61,139\n" +
                                                     "  \t  \tdarkslategray \t#2f4f4f \t47,79,79\n" +
                                                     "  \t  \tdarkslategrey \t#2f4f4f \t47,79,79\n" +
                                                     "  \t  \tdarkturquoise \t#00ced1 \t0,206,209\n" +
                                                     "  \t  \tdarkviolet \t#9400d3 \t148,0,211\n" +
                                                     "  \t  \tdeeppink \t#ff1493 \t255,20,147\n" +
                                                     "  \t  \tdeepskyblue \t#00bfff \t0,191,255\n" +
                                                     "  \t  \tdimgray \t#696969 \t105,105,105\n" +
                                                     "  \t  \tdimgrey \t#696969 \t105,105,105\n" +
                                                     "  \t  \tdodgerblue \t#1e90ff \t30,144,255\n" +
                                                     "  \t  \tfirebrick \t#b22222 \t178,34,34\n" +
                                                     "  \t  \tfloralwhite \t#fffaf0 \t255,250,240\n" +
                                                     "  \t  \tforestgreen \t#228b22 \t34,139,34\n" +
                                                     "  \t  \tfuchsia \t#ff00ff \t255,0,255\n" +
                                                     "  \t  \tgainsboro \t#dcdcdc \t220,220,220\n" +
                                                     "  \t  \tghostwhite \t#f8f8ff \t248,248,255\n" +
                                                     "  \t  \tgold \t#ffd700 \t255,215,0\n" +
                                                     "  \t  \tgoldenrod \t#daa520 \t218,165,32\n" +
                                                     "  \t  \tgray \t#808080 \t128,128,128\n" +
                                                     "  \t  \tgrey \t#808080 \t128,128,128\n" +
                                                     "  \t  \tgreen \t#008000 \t0,128,0\n" +
                                                     "  \t  \tgreenyellow \t#adff2f \t173,255,47\n" +
                                                     "  \t  \thoneydew \t#f0fff0 \t240,255,240\n" +
                                                     "  \t  \thotpink \t#ff69b4 \t255,105,180\n" +
                                                     "  \t  \tindianred \t#cd5c5c \t205,92,92\n" +
                                                     "  \t  \tindigo \t#4b0082 \t75,0,130\n" +
                                                     "  \t  \tivory \t#fffff0 \t255,255,240\n" +
                                                     "  \t  \tkhaki \t#f0e68c \t240,230,140\n" +
                                                     "  \t  \tlavender \t#e6e6fa \t230,230,250\n" +
                                                     "  \t  \tlavenderblush \t#fff0f5 \t255,240,245\n" +
                                                     "  \t  \tlawngreen \t#7cfc00 \t124,252,0\n" +
                                                     "  \t  \tlemonchiffon \t#fffacd \t255,250,205\n" +
                                                     "  \t  \tlightblue \t#add8e6 \t173,216,230\n" +
                                                     "  \t  \tlightcoral \t#f08080 \t240,128,128\n" +
                                                     "  \t  \tlightcyan \t#e0ffff \t224,255,255\n" +
                                                     "  \t  \tlightgoldenrodyellow \t#fafad2 \t250,250,210\n" +
                                                     "  \t  \tlightgray \t#d3d3d3 \t211,211,211\n" +
                                                     "  \t  \tlightgrey \t#d3d3d3 \t211,211,211\n" +
                                                     "  \t  \tlightgreen \t#90ee90 \t144,238,144\n" +
                                                     "  \t  \tlightpink \t#ffb6c1 \t255,182,193\n" +
                                                     "  \t  \tlightsalmon \t#ffa07a \t255,160,122\n" +
                                                     "  \t  \tlightseagreen \t#20b2aa \t32,178,170\n" +
                                                     "  \t  \tlightskyblue \t#87cefa \t135,206,250\n" +
                                                     "  \t  \tlightslategray \t#778899 \t119,136,153\n" +
                                                     "  \t  \tlightslategrey \t#778899 \t119,136,153\n" +
                                                     "  \t  \tlightsteelblue \t#b0c4de \t176,196,222\n" +
                                                     "  \t  \tlightyellow \t#ffffe0 \t255,255,224\n" +
                                                     "  \t  \tlime \t#00ff00 \t0,255,0\n" +
                                                     "  \t  \tlimegreen \t#32cd32 \t50,205,50\n" +
                                                     "  \t  \tlinen \t#faf0e6 \t250,240,230\n" +
                                                     "  \t  \tmagenta \t#ff00ff \t255,0,255\n" +
                                                     "  \t  \tmaroon \t#800000 \t128,0,0\n" +
                                                     "  \t  \tmediumaquamarine \t#66cdaa \t102,205,170\n" +
                                                     "  \t  \tmediumblue \t#0000cd \t0,0,205\n" +
                                                     "  \t  \tmediumorchid \t#ba55d3 \t186,85,211\n" +
                                                     "  \t  \tmediumpurple \t#9370db \t147,112,219\n" +
                                                     "  \t  \tmediumseagreen \t#3cb371 \t60,179,113\n" +
                                                     "  \t  \tmediumslateblue \t#7b68ee \t123,104,238\n" +
                                                     "  \t  \tmediumspringgreen \t#00fa9a \t0,250,154\n" +
                                                     "  \t  \tmediumturquoise \t#48d1cc \t72,209,204\n" +
                                                     "  \t  \tmediumvioletred \t#c71585 \t199,21,133\n" +
                                                     "  \t  \tmidnightblue \t#191970 \t25,25,112\n" +
                                                     "  \t  \tmintcream \t#f5fffa \t245,255,250\n" +
                                                     "  \t  \tmistyrose \t#ffe4e1 \t255,228,225\n" +
                                                     "  \t  \tmoccasin \t#ffe4b5 \t255,228,181\n" +
                                                     "  \t  \tnavajowhite \t#ffdead \t255,222,173\n" +
                                                     "  \t  \tnavy \t#000080 \t0,0,128\n" +
                                                     "  \t  \toldlace \t#fdf5e6 \t253,245,230\n" +
                                                     "  \t  \tolive \t#808000 \t128,128,0\n" +
                                                     "  \t  \tolivedrab \t#6b8e23 \t107,142,35\n" +
                                                     "  \t  \torange \t#ffa500 \t255,165,0\n" +
                                                     "  \t  \torangered \t#ff4500 \t255,69,0\n" +
                                                     "  \t  \torchid \t#da70d6 \t218,112,214\n" +
                                                     "  \t  \tpalegoldenrod \t#eee8aa \t238,232,170\n" +
                                                     "  \t  \tpalegreen \t#98fb98 \t152,251,152\n" +
                                                     "  \t  \tpaleturquoise \t#afeeee \t175,238,238\n" +
                                                     "  \t  \tpalevioletred \t#db7093 \t219,112,147\n" +
                                                     "  \t  \tpapayawhip \t#ffefd5 \t255,239,213\n" +
                                                     "  \t  \tpeachpuff \t#ffdab9 \t255,218,185\n" +
                                                     "  \t  \tperu \t#cd853f \t205,133,63\n" +
                                                     "  \t  \tpink \t#ffc0cb \t255,192,203\n" +
                                                     "  \t  \tplum \t#dda0dd \t221,160,221\n" +
                                                     "  \t  \tpowderblue \t#b0e0e6 \t176,224,230\n" +
                                                     "  \t  \tpurple \t#800080 \t128,0,128\n" +
                                                     "  \t  \tred \t#ff0000 \t255,0,0\n" +
                                                     "  \t  \trebeccapurple \t#663399 \t102,51,153\n" +
                                                     "  \t  \trosybrown \t#bc8f8f \t188,143,143\n" +
                                                     "  \t  \troyalblue \t#4169e1 \t65,105,225\n" +
                                                     "  \t  \tsaddlebrown \t#8b4513 \t139,69,19\n" +
                                                     "  \t  \tsalmon \t#fa8072 \t250,128,114\n" +
                                                     "  \t  \tsandybrown \t#f4a460 \t244,164,96\n" +
                                                     "  \t  \tseagreen \t#2e8b57 \t46,139,87\n" +
                                                     "  \t  \tseashell \t#fff5ee \t255,245,238\n" +
                                                     "  \t  \tsienna \t#a0522d \t160,82,45\n" +
                                                     "  \t  \tsilver \t#c0c0c0 \t192,192,192\n" +
                                                     "  \t  \tskyblue \t#87ceeb \t135,206,235\n" +
                                                     "  \t  \tslateblue \t#6a5acd \t106,90,205\n" +
                                                     "  \t  \tslategray \t#708090 \t112,128,144\n" +
                                                     "  \t  \tslategrey \t#708090 \t112,128,144\n" +
                                                     "  \t  \tsnow \t#fffafa \t255,250,250\n" +
                                                     "  \t  \tspringgreen \t#00ff7f \t0,255,127\n" +
                                                     "  \t  \tsteelblue \t#4682b4 \t70,130,180\n" +
                                                     "  \t  \ttan \t#d2b48c \t210,180,140\n" +
                                                     "  \t  \tteal \t#008080 \t0,128,128\n" +
                                                     "  \t  \tthistle \t#d8bfd8 \t216,191,216\n" +
                                                     "  \t  \ttomato \t#ff6347 \t255,99,71\n" +
                                                     "  \t  \tturquoise \t#40e0d0 \t64,224,208\n" +
                                                     "  \t  \tviolet \t#ee82ee \t238,130,238\n" +
                                                     "  \t  \twheat \t#f5deb3 \t245,222,179\n" +
                                                     "  \t  \twhite \t#ffffff \t255,255,255\n" +
                                                     "  \t  \twhitesmoke \t#f5f5f5 \t245,245,245\n" +
                                                     "  \t  \tyellow \t#ffff00 \t255,255,0\n" +
                                                     "  \t  \tyellowgreen \t#9acd32 \t154,205,50";
  private static final ArrayList<String> ourSystemColors;
  private static final List<String> ourStandardColors;

  static {
    ourSystemColors = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(systemColorsString, "\n");

    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();
      ourSystemColors.add(name.toLowerCase(Locale.US));
      tokenizer.nextToken();
    }

    ourStandardColors = new ArrayList<>();
    tokenizer = new StringTokenizer(standardColorsString, ", \n");

    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();
      ourStandardColors.add(name);
      tokenizer.nextToken();
    }

    getColors();
  }

  public static synchronized void getColors() {
    StringTokenizer tokenizer = new StringTokenizer(standardColorsString, ", \n");
    HashMap<String, String> standardColors = new HashMap<>();

    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();
      String value = tokenizer.nextToken();
      standardColors.put(name, name);
      ourColorNameToHexCodeMap.put(name, value);
      ourHexCodeToColorNameMap.put(value, name);
    }

    tokenizer = new StringTokenizer(colorsString, " \t\n");

    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();
      String hexValue = tokenizer.nextToken();

      tokenizer.nextToken(); // skip rgb

      if (!standardColors.containsKey(name)) {
        ourColorNameToHexCodeMap.put(name, hexValue);
        ourHexCodeToColorNameMap.put(hexValue, name);
      }
    }
  }

  public static boolean isSystemColorName(@NotNull @NonNls final String s) {
    return ourSystemColors.contains(s);
  }

  public static boolean isStandardColor(@NotNull @NonNls final String s) {
    return ourStandardColors.contains(s);
  }

  public static synchronized String getHexCodeForColorName(String colorName) {
    return ourColorNameToHexCodeMap.get(colorName);
  }

  public static synchronized String getColorNameForHexCode(String hexString) {
    return ourHexCodeToColorNameMap.get(hexString);
  }

  public static Color getColor(String text) {
    if (StringUtil.isEmptyOrSpaces(text)) {
      return null;
    }
    String hexValue = text.charAt(0) == '#' ? text : getHexCodeForColorName(text.toLowerCase(Locale.US));
    if (hexValue != null) {
      return ColorUtil.fromHex(hexValue, null);
    }
    return null;
  }
}
