package org.jetbrains.plugins.textmate.language.syntax.selector;

public class TextMateSelectorCachingWeigherTest extends TextMateSelectorWeigherTestCase {
  @Override
  protected TextMateSelectorWeigher createWeigher() {
    return new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());
  }
}
