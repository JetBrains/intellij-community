package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class XmlAttributeTokenizer  extends Tokenizer<XmlAttributeValue>{


  @Nullable
  @Override
  public Token[] tokenize(@NotNull XmlAttributeValue element) {
    return new Token[]{new Token<XmlAttributeValue>(element, element.getText(),false)};
  }


}