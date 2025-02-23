// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.stream.Collectors;

public class YAMLElementGenerator {
  private final Project myProject;

  public YAMLElementGenerator(Project project) {
    myProject = project;
  }

  public static YAMLElementGenerator getInstance(Project project) {
    return project.getService(YAMLElementGenerator.class);
  }

  public static @NotNull String createChainedKey(@NotNull List<String> keyComponents, int indentAddition) {
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

  public YAMLKeyValue createYamlKeyValueWithSequence(@NotNull String keyName, @NotNull Map<String, String> elementsMap) {
    String yamlString = elementsMap
      .entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue()))
      .collect(Collectors.joining("\n"));
    return createYamlKeyValue(keyName, yamlString);
  }

  public @NotNull YAMLKeyValue createYamlKeyValue(@NotNull String keyName, @NotNull String valueText) {
    final PsiFile tempValueFile = createDummyYamlWithText(valueText);
    Collection<YAMLValue> values = PsiTreeUtil.collectElementsOfType(tempValueFile, YAMLValue.class);

    String text;
    if (values.isEmpty()) {
      text = keyName + ":";
    }
    else if (values.iterator().next() instanceof YAMLScalar && !valueText.contains("\n")) {
      text = keyName + ": " + valueText;
    }
    else {
      text = keyName + ":\n" + YAMLTextUtil.indentText(valueText, 2);
    }

    final PsiFile tempFile = createDummyYamlWithText(text);
    return PsiTreeUtil.collectElementsOfType(tempFile, YAMLKeyValue.class).iterator().next();
  }

  public @NotNull YAMLQuotedTextImpl createYamlDoubleQuotedString() {
    final YAMLFile tempFile = createDummyYamlWithText("\"foo\"");
    return PsiTreeUtil.collectElementsOfType(tempFile, YAMLQuotedTextImpl.class).iterator().next();
  }

  public @NotNull YAMLFile createDummyYamlWithText(@NotNull String text) {
    return (YAMLFile) PsiFileFactory.getInstance(myProject)
      .createFileFromText("temp." + YAMLFileType.YML.getDefaultExtension(), YAMLFileType.YML, text, LocalTimeCounter.currentTime(), false);
  }

  public @NotNull PsiElement createEol() {
    final YAMLFile file = createDummyYamlWithText("\n");
    return PsiTreeUtil.getDeepestFirst(file);
  }

  public @NotNull PsiElement createSpace() {
    final YAMLKeyValue keyValue = createYamlKeyValue("foo", "bar");
    final ASTNode whitespaceNode = keyValue.getNode().findChildByType(TokenType.WHITE_SPACE);
    assert whitespaceNode != null;
    return whitespaceNode.getPsi();
  }

  public @NotNull PsiElement createIndent(int size) {
    final YAMLFile file = createDummyYamlWithText(StringUtil.repeatSymbol(' ', size));
    return PsiTreeUtil.getDeepestFirst(file);
  }

  public @NotNull PsiElement createColon() {
    final YAMLFile file = createDummyYamlWithText("? foo : bar");
    final PsiElement at = file.findElementAt("? foo ".length());
    assert at != null && at.getNode().getElementType() == YAMLTokenTypes.COLON;
    return at;
  }

  public @NotNull PsiElement createComma() {
    final YAMLFile file = createDummyYamlWithText("[1,2]");
    final PsiElement comma = file.findElementAt("[1".length());
    assert comma != null && comma.getNode().getElementType() == YAMLTokenTypes.COMMA;
    return comma;
  }

  public @NotNull PsiElement createDocumentMarker() {
    final YAMLFile file = createDummyYamlWithText("---");
    PsiElement at = file.findElementAt(0);
    assert at != null && at.getNode().getElementType() == YAMLTokenTypes.DOCUMENT_MARKER;
    return at;
  }

  public @NotNull YAMLSequence createEmptySequence() {
    YAMLSequence sequence = PsiTreeUtil.findChildOfType(createDummyYamlWithText("- dummy"), YAMLSequence.class);
    assert sequence != null;
    sequence.deleteChildRange(sequence.getFirstChild(), sequence.getLastChild());
    return sequence;
  }

  public @NotNull YAMLSequence createEmptyArray() {
    YAMLSequence sequence = PsiTreeUtil.findChildOfType(createDummyYamlWithText("[]"), YAMLSequence.class);
    assert sequence != null;
    return sequence;
  }

  public @NotNull YAMLSequenceItem createEmptySequenceItem() {
    YAMLSequenceItem sequenceItem = PsiTreeUtil.findChildOfType(createDummyYamlWithText("- dummy"), YAMLSequenceItem.class);
    assert sequenceItem != null;
    YAMLValue value = sequenceItem.getValue();
    assert value != null;
    value.deleteChildRange(value.getFirstChild(), value.getLastChild());
    return sequenceItem;
  }

  public @NotNull YAMLSequenceItem createSequenceItem(String text) {
    YAMLSequenceItem sequenceItem = PsiTreeUtil.findChildOfType(createDummyYamlWithText("- " + text), YAMLSequenceItem.class);
    assert sequenceItem != null;
    YAMLValue value = sequenceItem.getValue();
    assert value != null;
    return sequenceItem;
  }

  public @NotNull YAMLSequenceItem createArrayItem(String text) {
    YAMLSequenceItem sequenceItem = PsiTreeUtil.findChildOfType(createDummyYamlWithText("[" + text + "]"), YAMLSequenceItem.class);
    assert sequenceItem != null;
    YAMLValue value = sequenceItem.getValue();
    assert value != null;
    return sequenceItem;
  }
}
