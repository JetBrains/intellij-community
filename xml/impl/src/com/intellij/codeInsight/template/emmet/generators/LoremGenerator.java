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
package com.intellij.codeInsight.template.emmet.generators;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;

import java.util.*;

/**
 * User: zolotov
 * Date: 1/31/13
 */
public class LoremGenerator {
  static final String[] COMMON_P = "lorem ipsum dolor sit amet consectetur adipisicing elit".split(" ");
  private static final String[] WORDS = new String[]{"exercitationem", "perferendis", "perspiciatis", "laborum", "eveniet",
    "sunt", "iure", "nam", "nobis", "eum", "cum", "officiis", "excepturi",
    "odio", "consectetur", "quasi", "aut", "quisquam", "vel", "eligendi",
    "itaque", "non", "odit", "tempore", "quaerat", "dignissimos",
    "facilis", "neque", "nihil", "expedita", "vitae", "vero", "ipsum",
    "nisi", "animi", "cumque", "pariatur", "velit", "modi", "natus",
    "iusto", "eaque", "sequi", "illo", "sed", "ex", "et", "voluptatibus",
    "tempora", "veritatis", "ratione", "assumenda", "incidunt", "nostrum",
    "placeat", "aliquid", "fuga", "provident", "praesentium", "rem",
    "necessitatibus", "suscipit", "adipisci", "quidem", "possimus",
    "voluptas", "debitis", "sint", "accusantium", "unde", "sapiente",
    "voluptate", "qui", "aspernatur", "laudantium", "soluta", "amet",
    "quo", "aliquam", "saepe", "culpa", "libero", "ipsa", "dicta",
    "reiciendis", "nesciunt", "doloribus", "autem", "impedit", "minima",
    "maiores", "repudiandae", "ipsam", "obcaecati", "ullam", "enim",
    "totam", "delectus", "ducimus", "quis", "voluptates", "dolores",
    "molestiae", "harum", "dolorem", "quia", "voluptatem", "molestias",
    "magni", "distinctio", "omnis", "illum", "dolorum", "voluptatum", "ea",
    "quas", "quam", "corporis", "quae", "blanditiis", "atque", "deserunt",
    "laboriosam", "earum", "consequuntur", "hic", "cupiditate",
    "quibusdam", "accusamus", "ut", "rerum", "error", "minus", "eius",
    "ab", "ad", "nemo", "fugit", "officia", "at", "in", "id", "quos",
    "reprehenderit", "numquam", "iste", "fugiat", "sit", "inventore",
    "beatae", "repellendus", "magnam", "recusandae", "quod", "explicabo",
    "doloremque", "aperiam", "consequatur", "asperiores", "commodi",
    "optio", "dolor", "labore", "temporibus", "repellat", "veniam",
    "architecto", "est", "esse", "mollitia", "nulla", "a", "similique",
    "eos", "alias", "dolore", "tenetur", "deleniti", "porro", "facere",
    "maxime", "corrupti"};

  private final Random random;

  public LoremGenerator() {
    random = new Random();
  }

  /**
   * Generate a paragraph of Lorem ipsum
   *
   * @param wordsCount      words count in paragraph
   * @param startWithCommon should paragraph start with common {@link this#COMMON_P}
   * @return generated paragraph
   */
  public String generate(int wordsCount, boolean startWithCommon) {
    Collection<String> sentences = new LinkedList<>();
    int totalWords = 0;
    String[] words;

    if (startWithCommon) {
      words = Arrays.copyOfRange(COMMON_P, 0, Math.min(wordsCount, COMMON_P.length));
      if (words.length > 5) {
        words[4] += ',';
      }
      totalWords += words.length;
      sentences.add(sentence(words, '.'));
    }

    while (totalWords < wordsCount) {
      words = sample(WORDS, Math.min(rand(3, 12) * rand(1, 5), wordsCount - totalWords));
      totalWords += words.length;
      insertCommas(words);
      sentences.add(sentence(words));
    }

    return StringUtil.join(sentences, " ");
  }

  private void insertCommas(String[] words) {
    if (words.length <= 1) {
      return;
    }

    int len = words.length;
    int totalCommas;

    if (len > 3 && len <= 6) {
      totalCommas = rand(0, 1);
    }
    else if (len > 6 && len <= 12) {
      totalCommas = rand(0, 2);
    }
    else {
      totalCommas = rand(1, 4);
    }

    while (totalCommas > 0) {
      int i = rand(0, words.length - 1);
      String word = words[i];
      if (!StringUtil.endsWithChar(word, ',')) {
        words[i] = word + ",";
      }
      totalCommas--;
    }
  }

  private String sentence(String[] words) {
    return sentence(words, choice("?!..."));
  }

  private static String sentence(String[] words, char endChar) {
    if (words.length > 0) {
      words[0] = StringUtil.capitalize(words[0]);
    }

    return StringUtil.join(words, " ") + endChar;
  }

  private int rand(int from, int to) {
    return random.nextInt(to - from) + from;
  }

  private String[] sample(String[] words, int wordsCount) {
    int len = words.length;
    int iterations = Math.min(len, wordsCount);
    Set<String> result = new TreeSet<>();
    while (result.size() < iterations) {
      int i = rand(0, len - 1);
      result.add(words[i]);
    }

    return ArrayUtil.toStringArray(result);
  }

  private char choice(String values) {
    return values.charAt(rand(0, values.length() - 1));
  }
}
