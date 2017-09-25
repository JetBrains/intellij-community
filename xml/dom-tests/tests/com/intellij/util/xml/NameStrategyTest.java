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
package com.intellij.util.xml;

import junit.framework.TestCase;

/**
 * @author peter
 */
public class NameStrategyTest extends TestCase {

  public void testHyphenStrategy() {
    assertEquals("aaa-bbb-ccc", DomNameStrategy.HYPHEN_STRATEGY.convertName("aaaBbbCcc"));
    assertEquals("aaa-bbb-ccc", DomNameStrategy.HYPHEN_STRATEGY.convertName("AaaBbbCcc"));
    assertEquals("aaa", DomNameStrategy.HYPHEN_STRATEGY.convertName("Aaa"));
    assertEquals("aaa", DomNameStrategy.HYPHEN_STRATEGY.convertName("aaa"));
    assertEquals("AAA-bbb", DomNameStrategy.HYPHEN_STRATEGY.convertName("AAABbb"));
    assertEquals("aaa-BBB", DomNameStrategy.HYPHEN_STRATEGY.convertName("AaaBBB"));
  }

  public void testHyphenStrategySplit() {
    assertEquals("aaa bbb ccc", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("aaa-bbb-ccc"));
    assertEquals("Aaa", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("Aaa"));
    assertEquals("aaa", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("aaa"));
    assertEquals("AAA bbb", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("AAA-bbb"));
    assertEquals("aaa BBB", DomNameStrategy.HYPHEN_STRATEGY.splitIntoWords("aaa-BBB"));
  }

  public void testJavaStrategy() {
    assertEquals("aaaBbbCcc", DomNameStrategy.JAVA_STRATEGY.convertName("aaaBbbCcc"));
    assertEquals("aaaBbbCcc", DomNameStrategy.JAVA_STRATEGY.convertName("AaaBbbCcc"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.convertName("Aaa"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.convertName("aaa"));
    assertEquals("AAABbb", DomNameStrategy.JAVA_STRATEGY.convertName("AAABbb"));
    assertEquals("aaaBBB", DomNameStrategy.JAVA_STRATEGY.convertName("AaaBBB"));
  }

  public void testJavaStrategySplit() {
    assertEquals("aaa bbb ccc", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("aaaBbbCcc"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("Aaa"));
    assertEquals("aaa", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("aaa"));
    assertEquals("AAA bbb", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("AAABbb"));
    assertEquals("aaa BBB", DomNameStrategy.JAVA_STRATEGY.splitIntoWords("aaaBBB"));
  }

}
