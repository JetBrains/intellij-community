package com.intellij.codeInsight.template.emmet.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.ZenCodingTemplate;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.emmet.tokens.TextToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class TextNode extends ZenCodingNode {
  private final String myText;

  public TextNode(@NotNull TextToken textToken) {
    final String text = textToken.getText();
    myText = text.substring(1, text.length() - 1);
  }

  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration,
                                     String surroundedText,
                                     CustomTemplateCallback callback,
                                     boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    final TemplateToken templateToken = TemplateToken.EMPTY_TEMPLATE_TOKEN;
    final boolean containsSurroundedTextMarker = ZenCodingUtil.containsSurroundedTextMarker(myText);

    final String text = ZenCodingUtil.replaceMarkers(myText.replace("${nl}", "\n"), numberInIteration, surroundedText);
    final TemplateImpl template = new TemplateImpl("", text, "");
    ZenCodingTemplate.doSetTemplate(templateToken, template, callback);

    final GenerationNode node = new GenerationNode(templateToken, numberInIteration,
                                                   containsSurroundedTextMarker ? null : surroundedText,
                                                   insertSurroundedTextAtTheEnd, parent);
    return Collections.singletonList(node);
  }

  @Override
  public String toString() {
    return "Text(" + myText + ")";
  }
}
