package org.jetbrains.yaml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.impl.YAMLQuotedTextImpl;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class YAMLElementGenerator {
  private final Project myProject;

  public YAMLElementGenerator(Project project) {
    myProject = project;
  }

  public static YAMLElementGenerator getInstance(Project project) {
    return ServiceManager.getService(project, YAMLElementGenerator.class);
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

  @NotNull
  public YAMLKeyValue createYamlKeyValue(@NotNull String keyName, @NotNull String valueText) {
    final PsiFile tempValueFile = createDummyYamlWithText(valueText);
    Collection<YAMLValue> values = PsiTreeUtil.collectElementsOfType(tempValueFile, YAMLValue.class);

    String text;
    if (!values.isEmpty() && values.iterator().next() instanceof YAMLScalar && !valueText.contains("\n")) {
      text = keyName + ": " + valueText;
    }
    else {
      text = keyName + ":\n" + indentText(valueText);
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
      .createFileFromText("temp." + YAMLFileType.YML.getDefaultExtension(), YAMLFileType.YML, text, LocalTimeCounter.currentTime(), true);
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
  private static String indentText(@NotNull String text) {
    StringBuilder buffer = new StringBuilder();
    String indentString = StringUtil.repeatSymbol(' ', 2);
    buffer.append(indentString);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      buffer.append(c);
      if (c == '\n') {
        buffer.append(indentString);
      }
    }
    return buffer.toString();
  }
}
