/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.ide.ui.search;

import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"HardCodedStringLiteral"})
public class PorterStemmerUtil {
  private PorterStemmerUtil() {
  }

  @Nullable
  public static String stem(String str) {
    // check for zero length
    final int strLen = str.length();
    if (strLen > 0) {
      int lastDigit = -1;
      for (int i = 0; i < strLen; ++i) {
        char c = str.charAt(i);
        if (Character.isDigit(c)) {
          lastDigit = i;
        }
        else if (!Character.isLetter(c)) {
          return null;
        }
      }
      ++lastDigit;
      if (lastDigit > 0 && lastDigit < strLen) {
        return str.substring(0, lastDigit) + stemString(str.substring(lastDigit));
      }
      return stemString(str);
    }
    return null;
  }

  private static String stemString(String str) {
    str = step1a(str);
    str = step1b(str);
    str = step1c(str);
    str = step2(str);
    str = step3(str);
    str = step4(str);
    str = step5a(str);
    str = step5b(str);
    return str;
  }

  private static String step1a(String str) {
    // SSES -> SS
    if (str.endsWith("sses")) {
      return str.substring(0, str.length() - 2);
      // IES -> I
    }
    else if (str.endsWith("ies")) {
      return str.substring(0, str.length() - 2);
      // SS -> S
    }
    else if (str.endsWith("ss")) {
      return str;
      // S ->
    }
    else if (str.endsWith("s")) {
      return str.substring(0, str.length() - 1);
    }
    else {
      return str;
    }
  }

  private static String step1b(String str) {
    // (m > 0) EED -> EE
    if (str.endsWith("eed")) {
      if (stringMeasure(str.substring(0, str.length() - 3)) > 0) {
        return str.substring(0, str.length() - 1);
      }
      else {
        return str;
      }
      // (*v*) ED ->
    }
    else if ((str.endsWith("ed")) && (containsVowel(str.substring(0, str.length() - 2)))) {
      return step1b2(str.substring(0, str.length() - 2));
      // (*v*) ING ->
    }
    else if ((str.endsWith("ing")) && (containsVowel(str.substring(0, str.length() - 3)))) {
      return step1b2(str.substring(0, str.length() - 3));
    }
    return str;
  }

  private static String step1b2(String str) {
    // AT -> ATE
    if (str.endsWith("at") || str.endsWith("bl") || str.endsWith("iz")) {
      return str + "e";
    }
    else if ((endsWithDoubleConsonent(str)) && (!(str.endsWith("l") || str.endsWith("s") || str.endsWith("z")))) {
      return str.substring(0, str.length() - 1);
    }
    else if ((stringMeasure(str) == 1) && (endsWithCVC(str))) {
      return str + "e";
    }
    else {
      return str;
    }
  }

  private static String step1c(String str) {
    // (*v*) Y -> I
    if (str.endsWith("y")) {
      if (containsVowel(str.substring(0, str.length() - 1))) return str.substring(0, str.length() - 1) + "i";
    }
    return str;
  }

  private static String step2(String str) {
    // (m > 0) ATIONAL -> ATE
    if ((str.endsWith("ational")) && (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
      return str.substring(0, str.length() - 5) + "e";
      // (m > 0) TIONAL -> TION
    }
    else if ((str.endsWith("tional")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) ENCI -> ENCE
    }
    else if ((str.endsWith("enci")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) ANCI -> ANCE
    }
    else if ((str.endsWith("anci")) && (stringMeasure(str.substring(0, str.length() - 1)) > 0)) {
      return str.substring(0, str.length() - 1) + "e";
      // (m > 0) IZER -> IZE
    }
    else if ((str.endsWith("izer")) && (stringMeasure(str.substring(0, str.length() - 1)) > 0)) {
      return str.substring(0, str.length() - 1);
      // (m > 0) ABLI -> ABLE
    }
    else if ((str.endsWith("abli")) && (stringMeasure(str.substring(0, str.length() - 1)) > 0)) {
      return str.substring(0, str.length() - 1) + "e";
      // (m > 0) ENTLI -> ENT
    }
    else if ((str.endsWith("alli")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) ELI -> E
    }
    else if ((str.endsWith("entli")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) OUSLI -> OUS
    }
    else if ((str.endsWith("eli")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) IZATION -> IZE
    }
    else if ((str.endsWith("ousli")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) IZATION -> IZE
    }
    else if ((str.endsWith("ization")) && (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
      return str.substring(0, str.length() - 5) + "e";
      // (m > 0) ATION -> ATE
    }
    else if ((str.endsWith("ation")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3) + "e";
      // (m > 0) ATOR -> ATE
    }
    else if ((str.endsWith("ator")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2) + "e";
      // (m > 0) ALISM -> AL
    }
    else if ((str.endsWith("alism")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3);
      // (m > 0) IVENESS -> IVE
    }
    else if ((str.endsWith("iveness")) && (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
      return str.substring(0, str.length() - 4);
      // (m > 0) FULNESS -> FUL
    }
    else if ((str.endsWith("fulness")) && (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
      return str.substring(0, str.length() - 4);
      // (m > 0) OUSNESS -> OUS
    }
    else if ((str.endsWith("ousness")) && (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
      return str.substring(0, str.length() - 4);
      // (m > 0) ALITII -> AL
    }
    else if ((str.endsWith("aliti")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3);
      // (m > 0) IVITI -> IVE
    }
    else if ((str.endsWith("iviti")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3) + "e";
      // (m > 0) BILITI -> BLE
    }
    else if ((str.endsWith("biliti")) && (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
      return str.substring(0, str.length() - 5) + "le";
    }
    return str;
  }


  private static String step3(String str) {
    // (m > 0) ICATE -> IC
    if ((str.endsWith("icate")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3);
      // (m > 0) ATIVE ->
    }
    else if ((str.endsWith("ative")) && (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
      return str.substring(0, str.length() - 5);
      // (m > 0) ALIZE -> AL
    }
    else if ((str.endsWith("alize")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3);
      // (m > 0) ICITI -> IC
    }
    else if ((str.endsWith("iciti")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3);
      // (m > 0) ICAL -> IC
    }
    else if ((str.endsWith("ical")) && (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
      return str.substring(0, str.length() - 2);
      // (m > 0) FUL ->
    }
    else if ((str.endsWith("ful")) && (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
      return str.substring(0, str.length() - 3);
      // (m > 0) NESS ->
    }
    else if ((str.endsWith("ness")) && (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
      return str.substring(0, str.length() - 4);
    }
    return str;
  }


  private static String step4(String str) {
    if ((str.endsWith("al")) && (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
      return str.substring(0, str.length() - 2);
      // (m > 1) ANCE ->
    }
    else if ((str.endsWith("ance")) && (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
      return str.substring(0, str.length() - 4);
      // (m > 1) ENCE ->
    }
    else if ((str.endsWith("ence")) && (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
      return str.substring(0, str.length() - 4);
      // (m > 1) ER ->
    }
    else if ((str.endsWith("er")) && (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
      return str.substring(0, str.length() - 2);
      // (m > 1) IC ->
    }
    else if ((str.endsWith("ic")) && (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
      return str.substring(0, str.length() - 2);
      // (m > 1) ABLE ->
    }
    else if ((str.endsWith("able")) && (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
      return str.substring(0, str.length() - 4);
      // (m > 1) IBLE ->
    }
    else if ((str.endsWith("ible")) && (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
      return str.substring(0, str.length() - 4);
      // (m > 1) ANT ->
    }
    else if ((str.endsWith("ant")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) EMENT ->
    }
    else if ((str.endsWith("ement")) && (stringMeasure(str.substring(0, str.length() - 5)) > 1)) {
      return str.substring(0, str.length() - 5);
      // (m > 1) MENT ->
    }
    else if ((str.endsWith("ment")) && (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
      return str.substring(0, str.length() - 4);
      // (m > 1) ENT ->
    }
    else if ((str.endsWith("ent")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) and (*S or *T) ION ->
    }
    else if ((str.endsWith("sion") || str.endsWith("tion")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) OU ->
    }
    else if ((str.endsWith("ou")) && (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
      return str.substring(0, str.length() - 2);
      // (m > 1) ISM ->
    }
    else if ((str.endsWith("ism")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) ATE ->
    }
    else if ((str.endsWith("ate")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) ITI ->
    }
    else if ((str.endsWith("iti")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) OUS ->
    }
    else if ((str.endsWith("ous")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) IVE ->
    }
    else if ((str.endsWith("ive")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
      // (m > 1) IZE ->
    }
    else if ((str.endsWith("ize")) && (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
      return str.substring(0, str.length() - 3);
    }
    return str;
  }


  private static String step5a(String str) {
    // (m > 1) E ->
    if (str.endsWith("e") && stringMeasure(str.substring(0, str.length() - 1)) > 1) {
      return str.substring(0, str.length() - 1);
    }
    // (m = 1 and not *0) E ->
    else
    if (str.endsWith("e") && stringMeasure(str.substring(0, str.length() - 1)) == 1 && !endsWithCVC(str.substring(0, str.length() - 1))) {
      return str.substring(0, str.length() - 1);
    }
    else {
      return str;
    }
  }


  private static String step5b(String str) {
    // (m > 1 and *d and *L) ->
    if (str.endsWith("l") && stringMeasure(str.substring(0, str.length() - 1)) > 1 && endsWithDoubleConsonent(str)) {
      return str.substring(0, str.length() - 1);
    }
    else {
      return str;
    }
  }


  private static boolean containsVowel(String str) {
    char[] strchars = str.toCharArray();
    for (char strchar : strchars) {
      if (isVowel(strchar)) return true;
    }
    // no aeiou but there is y
    if (str.indexOf('y') > -1) {
      return true;
    }
    else {
      return false;
    }
  }

  private static boolean isVowel(char c) {
    if ((c == 'a') || (c == 'e') || (c == 'i') || (c == 'o') || (c == 'u')) {
      return true;
    }
    else {
      return false;
    }
  }

  private static boolean endsWithDoubleConsonent(String str) {
    char c = str.charAt(str.length() - 1);
    if (str.length() > 1 && c == str.charAt(str.length() - 2)) {
      if (!containsVowel(str.substring(str.length() - 2))) {
        return true;
      }
    }
    return false;
  }

  // returns a CVC measure for the string
  private static int stringMeasure(String str) {
    int count = 0;
    boolean vowelSeen = false;
    char[] strchars = str.toCharArray();

    for (char strchar : strchars) {
      if (isVowel(strchar)) {
        vowelSeen = true;
      }
      else if (vowelSeen) {
        count++;
        vowelSeen = false;
      }
    }
    return count;
  }

  private static boolean endsWithCVC(String str) {
    char c;
    char v;
    char c2;
    if (str.length() >= 3) {
      c = str.charAt(str.length() - 1);
      v = str.charAt(str.length() - 2);
      c2 = str.charAt(str.length() - 3);
    }
    else {
      return false;
    }

    if ((c == 'w') || (c == 'x') || (c == 'y')) {
      return false;
    }
    else if (isVowel(c)) {
      return false;
    }
    else if (!isVowel(v)) {
      return false;
    }
    else if (isVowel(c2)) {
      return false;
    }
    else {
      return true;
    }
  }
}
