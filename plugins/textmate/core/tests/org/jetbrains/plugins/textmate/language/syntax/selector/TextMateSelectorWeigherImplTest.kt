package org.jetbrains.plugins.textmate.language.syntax.selector;

public class TextMateSelectorWeigherImplTest extends TextMateSelectorWeigherTestCase {
  @Override
  protected TextMateSelectorWeigher createWeigher() {
    return new TextMateSelectorWeigherImpl();
  }
}
