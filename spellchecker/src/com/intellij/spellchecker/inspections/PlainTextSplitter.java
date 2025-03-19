// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jdom.Verifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.io.URLUtil.URL_PATTERN;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class PlainTextSplitter extends BaseSplitter {
  private static final PlainTextSplitter INSTANCE = new PlainTextSplitter();

  public static PlainTextSplitter getInstance() {
    return INSTANCE;
  }

  private static final Pattern SPLIT_PATTERN = Pattern.compile("(\\s|\b|\\(|\\))");

  private static final Pattern MAIL =
    Pattern.compile("([\\p{L}0-9\\.\\-\\_\\+]+@([\\p{L}0-9\\-\\_]+(\\.)?)+(com|net|[a-z]{2})?)");

  private static final int UUID_V4_HEX_STRING_LENGTH = 36;
  private static final Pattern UUID_PATTERN = Pattern.compile("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}");

  private static final int MD5_HEX_LENGTH = 32;
  private static final int SHA1_HEX_LENGTH = 40;
  private static final int SHA256_HEX_LENGTH = 64;
  private static final int SHA512_HEX_LENGTH = 128;

  private static final String HEX_SYMBOLS = "[0-9A-Fa-f]";

  private static final Pattern MD5_HEX_PATTERN = Pattern.compile(HEX_SYMBOLS + "{" + MD5_HEX_LENGTH + "}");
  private static final Pattern SHA1_HEX_PATTERN = Pattern.compile(HEX_SYMBOLS + "{" + SHA1_HEX_LENGTH + "}");
  private static final Pattern SHA256_HEX_PATTERN = Pattern.compile(HEX_SYMBOLS + "{" + SHA256_HEX_LENGTH + "}");
  private static final Pattern SHA512_HEX_PATTERN = Pattern.compile(HEX_SYMBOLS + "{" + SHA512_HEX_LENGTH + "}");

  private static final int SHA384_BASE64_LENGTH = 64;
  private static final String SHA384_PREFIX = "sha384-";
  private static final Pattern SHA384_PREFIXED_VALUE_PATTERN = Pattern.compile("sha384-[A-Za-z0-9+=/]{" + SHA384_BASE64_LENGTH + "}");

  private static final int SHA512_BASE64_LENGTH = 88;
  private static final String SHA512_PREFIX = "sha512-";
  private static final Pattern SHA512_PREFIXED_VALUE_PATTERN = Pattern.compile("sha512-[A-Za-z0-9+=/]{" + SHA512_BASE64_LENGTH + "}");

  private static final String JWT_COMMON_PREFIX = "eyJhbGci"; // Base64 of `{"alg":` in JWT header
  private static final Pattern JWT_PATTERN = Pattern.compile("[A-Za-z0-9+=/_\\-.]+");

  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (StringUtil.isEmpty(text)) {
      return;
    }
    final Splitter ws = getTextSplitter();
    int from = range.getStartOffset();
    int till;

    try {
      Matcher matcher;
      final String substring = range.substring(text).replace('\b', '\n').replace('\f', '\n');
      if (Verifier.checkCharacterData(SPLIT_PATTERN.matcher(newBombedCharSequence(substring)).replaceAll("")) != null) {
        return;
      }
      matcher = SPLIT_PATTERN.matcher(newBombedCharSequence(text, range));

      while (true) {
        ProgressManager.checkCanceled();

        List<TextRange> toCheck;
        TextRange wRange;
        String word;

        if (matcher.find()) {
          TextRange found = matcherRange(range, matcher);
          till = found.getStartOffset();
          if (badSize(from, till)) {
            from = found.getEndOffset();
            continue;
          }
          wRange = new TextRange(from, till);
          word = wRange.substring(text);
          from = found.getEndOffset();
        }
        else { // end hit or zero matches
          wRange = new TextRange(from, range.getEndOffset());
          word = wRange.substring(text);
        }

        int wordLength = word.length();

        if (word.contains("@")) {
          toCheck = excludeByPattern(text, wRange, MAIL, 0);
        }
        else if (word.contains("://")) {
          toCheck = excludeByPattern(text, wRange, URL_PATTERN, 0);
        }
        else if (word.startsWith(JWT_COMMON_PREFIX) && JWT_PATTERN.matcher(word).matches()) {
          toCheck = emptyList();
        }
        else if (wordLength == MD5_HEX_LENGTH && MD5_HEX_PATTERN.matcher(word).matches() ||
                 wordLength == SHA1_HEX_LENGTH && SHA1_HEX_PATTERN.matcher(word).matches() ||
                 wordLength == SHA256_HEX_LENGTH && SHA256_HEX_PATTERN.matcher(word).matches() ||
                 wordLength == SHA512_HEX_LENGTH && SHA512_HEX_PATTERN.matcher(word).matches()) {
          toCheck = emptyList();
        }
        else if (wordLength == UUID_V4_HEX_STRING_LENGTH && UUID_PATTERN.matcher(word).matches()) {
          toCheck = emptyList();
        }
        else if (isHashPrefixed(word, SHA384_PREFIX, SHA384_BASE64_LENGTH) && SHA384_PREFIXED_VALUE_PATTERN.matcher(word).matches()
                 || isHashPrefixed(word, SHA512_PREFIX, SHA512_BASE64_LENGTH) && SHA512_PREFIXED_VALUE_PATTERN.matcher(word).matches()) {
          toCheck = emptyList(); // various integrity
        }
        else {
          toCheck = singletonList(wRange);
        }

        for (TextRange r : toCheck) {
          ws.split(text, r, consumer);
        }

        if (matcher.hitEnd()) break;
      }
    }
    catch (TooLongBombedMatchingException ignored) {
    }
  }

  protected @NotNull Splitter getTextSplitter() {
    return TextSplitter.getInstance();
  }

  private static boolean isHashPrefixed(String text, String hashPrefix, int expectedHashSize) {
    return text.length() == expectedHashSize + hashPrefix.length()
           && text.startsWith(hashPrefix);
  }
}
