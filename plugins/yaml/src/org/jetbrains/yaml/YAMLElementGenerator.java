// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.*;
import org.jetbrains.yaml.psi.impl.YAMLQuotedTextImpl;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class YAMLElementGenerator {
  private final Project myProject;

  public YAMLElementGenerator(Project project) {
    myProject = project;
  }

  public static YAMLElementGenerator getInstance(Project project) {
    return project.getService(YAMLElementGenerator.class);
  }

  @NotNull
  public static String createChainedKey(@NotNull List<String> keyComponents, int indentAddition) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keyComponents.size(); ++i) {
      if (i > 0) {
        sb.append(StringUtil.repeatSymbol(' ', indentAddition + 2 * i));
      }
      sb.append(keyComponents.get(i)).append(":");
      if (i + 1 < keyComponents.size()) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  /**
   * Create a YAML key-value pair with a sequence as the value.
   * Example:
   * <pre>{@code
   * Given:
   * keyName = "fruits";
   * sequence = Map.of("apple","red", "banana","yellow");
   *
   * would generate YAML:
   * fruits:
   *   apple: red
   *   banana: yellow
   * }</pre>
   * @param keyName   The name of the key.
   * @param sequence  The sequence to be used as the value.
   * @return The created YAML key-value pair.
   */
  public YAMLKeyValue createYamlKeyValueWithSequence(@NotNull String keyName, @NotNull Map<String, String> sequence) {
    if (sequence.isEmpty()) return createYamlKeyValue(keyName, "");

    StringBuilder sb = new StringBuilder(keyName + ":\n");
    sequence.entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(entry -> sb.append(" %s: %s\n".formatted(entry.getKey(), entry.getValue())));

    YAMLKeyValue yamlKeyValueWithSequence = YAMLUtil.getTopLevelKeys(createDummyYamlWithText(sb.toString()))
      .stream().findFirst().orElseThrow(() -> new RuntimeException("Cannot create dummy YAML file using string: "+sb));
    return yamlKeyValueWithSequence;
  }

  @NotNull
  public YAMLKeyValue createYamlKeyValue(@NotNull String keyName, @NotNull String valueText) {
    final PsiFile tempValueFile = createDummyYamlWithText(valueText);
    Collection<YAMLValue> values = PsiTreeUtil.collectElementsOfType(tempValueFile, YAMLValue.class);

    String text;
    if (!values.isEmpty() && values.iterator().next() instanceof YAMLScalar && !valueText.contains("\n")) {
      text = keyName + ": " + valueText;
    }
    else {
      text = keyName + ":\n" + YAMLTextUtil.indentText(valueText, 2);
    }

    final PsiFile tempFile = createDummyYamlWithText(text);
    return PsiTreeUtil.collectElementsOfType(tempFile, YAMLKeyValue.class).iterator().next();
  }

  @NotNull
  public YAMLQuotedTextImpl createYamlDoubleQuotedString() {
    final YAMLFile tempFile = createDummyYamlWithText("\"foo\"");
    return PsiTreeUtil.collectElementsOfType(tempFile, YAMLQuotedTextImpl.class).iterator().next();
  }

  @NotNull
  public YAMLFile createDummyYamlWithText(@NotNull String text) {
    return (YAMLFile) PsiFileFactory.getInstance(myProject)
      .createFileFromText("temp." + YAMLFileType.YML.getDefaultExtension(), YAMLFileType.YML, text, LocalTimeCounter.currentTime(), false);
  }

  @NotNull
  public PsiElement createEol() {
    final YAMLFile file = createDummyYamlWithText("\n");
    return PsiTreeUtil.getDeepestFirst(file);
  }

  @NotNull
  public PsiElement createSpace() {
    final YAMLKeyValue keyValue = createYamlKeyValue("foo", "bar");
    final ASTNode whitespaceNode = keyValue.getNode().findChildByType(TokenType.WHITE_SPACE);
    assert whitespaceNode != null;
    return whitespaceNode.getPsi();
  }

  @NotNull
  public PsiElement createIndent(int size) {
    final YAMLFile file = createDummyYamlWithText(StringUtil.repeatSymbol(' ', size));
    return PsiTreeUtil.getDeepestFirst(file);
  }

  @NotNull
  public PsiElement createColon() {
    final YAMLFile file = createDummyYamlWithText("? foo : bar");
    final PsiElement at = file.findElementAt("? foo ".length());
    assert at != null && at.getNode().getElementType() == YAMLTokenTypes.COLON;
    return at;
  }
  @NotNull
  public PsiElement createDocumentMarker() {
    final YAMLFile file = createDummyYamlWithText("---");
    PsiElement at = file.findElementAt(0);
    assert at != null && at.getNode().getElementType() == YAMLTokenTypes.DOCUMENT_MARKER;
    return at;
  }

  @NotNull
  public YAMLSequence createEmptySequence() {
    YAMLSequence sequence = PsiTreeUtil.findChildOfType(createDummyYamlWithText("- dummy"), YAMLSequence.class);
    assert sequence != null;
    sequence.deleteChildRange(sequence.getFirstChild(), sequence.getLastChild());
    return sequence;
  }

  @NotNull
  public YAMLSequenceItem createEmptySequenceItem() {
    YAMLSequenceItem sequenceItem = PsiTreeUtil.findChildOfType(createDummyYamlWithText("- dummy"), YAMLSequenceItem.class);
    assert sequenceItem != null;
    YAMLValue value = sequenceItem.getValue();
    assert value != null;
    value.deleteChildRange(value.getFirstChild(), value.getLastChild());
    return sequenceItem;
  }
}
