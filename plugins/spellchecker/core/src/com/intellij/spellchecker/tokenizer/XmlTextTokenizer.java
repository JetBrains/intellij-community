package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class XmlTextTokenizer extends Tokenizer<XmlText> {


  @Nullable
  @Override
  public Token[] tokenize(@NotNull XmlText element) {
    return new Token[]{new Token<XmlText>(element, element.getText(),false)};
  }


}