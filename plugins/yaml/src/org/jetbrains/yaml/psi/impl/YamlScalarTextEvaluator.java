package org.jetbrains.yaml.psi.impl;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jetbrains.yaml.psi.impl.YAMLScalarImpl.processReplacements;

/**
 * Yaml text value evaluator according to the YAML Literal values specification https://yaml.org/spec/1.2/spec.html#Block
 */
public abstract class YamlScalarTextEvaluator<T extends YAMLScalarImpl> {

  protected final @NotNull T myHost;

  public YamlScalarTextEvaluator(@NotNull T host) {
    myHost = host;
  }

  @NotNull
  public abstract List<TextRange> getContentRanges();

  @NotNull
  protected abstract String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore);

  @NotNull
  public String getTextValue(@Nullable TextRange rangeInHost) {
    final String text = myHost.getText();
    final List<TextRange> contentRanges = getContentRanges();

    final StringBuilder builder = new StringBuilder();

    for (int i = 0; i < contentRanges.size(); i++) {
      final TextRange range = rangeInHost != null ? rangeInHost.intersection(contentRanges.get(i)) : contentRanges.get(i);
      if (range == null) continue;

      final CharSequence curString = range.subSequence(text);
      builder.append(curString);

      if (range.getEndOffset() == contentRanges.get(i).getEndOffset() && i + 1 != contentRanges.size()) {
        builder.append(getRangesJoiner(text, contentRanges, i));
      }
    }
    return processReplacements(builder, myHost.getDecodeReplacements(builder));
  }
  
}