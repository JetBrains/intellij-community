package com.intellij.bash.shellcheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.bash.parser.BashShebangParserUtil;
import com.intellij.bash.psi.BashFile;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BashShellcheckExternalAnnotator extends ExternalAnnotator<String, Collection<BashShellcheckExternalAnnotator.Result>> {

  private static final List<String> KNOWN_SHELLS = ContainerUtil.list("bash", "dash", "ksh", "sh");
  private String shebangText;

  @Override
  public String getPairedBatchInspectionShortName() {
    return BashShellcheckInspection.SHORT_NAME;
  }

  @Nullable
  @Override
  public String collectInformation(@NotNull PsiFile file) {
    if (file instanceof BashFile) {
      readShebang(file);
      return file.getText();
    }
    return null;
  }

  @Nullable
  @Override
  public String collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    if (file instanceof BashFile) {
      readShebang(file);
    }
    return editor.getDocument().getText();
  }

  @Nullable
  @Override
  public Collection<BashShellcheckExternalAnnotator.Result> doAnnotate(String fileContent) {
    String shellcheckExecutable = BashShellcheckUtil.getShellcheckPath();
    if (!BashShellcheckUtil.isValidPath(shellcheckExecutable)) {
      return null;
    }

    try {
      String interpreter = BashShebangParserUtil.getInterpreter(shebangText);
      if (interpreter == null || !KNOWN_SHELLS.contains(interpreter)) {
        interpreter = "bash";
      }

      GeneralCommandLine commandLine = new GeneralCommandLine()
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withExePath(shellcheckExecutable)
          .withParameters(
              "--color=never", "--format=json", "--severity=style", "--shell="+interpreter, "--wiki-link-count=10",
              "--exclude=SC1091",
              "-");
      Process process = commandLine.createProcess();
      writeFileContentToStdin(process, fileContent, commandLine.getCharset());
      if (process.waitFor(10, TimeUnit.SECONDS)) {
        String output = StreamUtil.readText(process.getInputStream(), commandLine.getCharset());
        Type type = TypeToken.getParameterized(List.class, Result.class).getType();
        return new Gson().fromJson(output, type);
      }
      return null;
    }
    catch (IOException | ExecutionException | InterruptedException e) {
      // todo: add notification
      return null;
    }
  }

  @Override
  public void apply(@NotNull PsiFile file, Collection<Result> annotationResult, @NotNull AnnotationHolder holder) {
    super.apply(file, annotationResult, holder);
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }
    for (Result result : annotationResult) {
      CharSequence sequence = document.getCharsSequence();
      int startOffset = calcOffset(sequence, document.getLineStartOffset(result.line - 1), result.column);
      int endOffset = calcOffset(sequence, document.getLineStartOffset(result.endLine - 1), result.endColumn);
      TextRange range = TextRange.create(startOffset, endOffset == startOffset ? endOffset + 1 : endOffset);
      long code = result.code;
      String message = result.message;
      String scCode = "SC" + code;
      String html =
          "<html>" +
              "<p>" + StringUtil.escapeXmlEntities(message) + "</p>" +
              "<p>See <a href='https://github.com/koalaman/shellcheck/wiki/SC" + code + "'>" + scCode + "</a>.</p>" +
              "</html>";
      holder.createAnnotation(severity(result.level), range, message, html).registerFix(new IntentionAction() {
        @NotNull
        @Override
        public String getText() {
          return "Suppress shellcheck inspection " + scCode;
        }

        @NotNull
        @Override
        public String getFamilyName() {
          return "Bash";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
          return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          int lineStartOffset = DocumentUtil.getLineStartOffset(startOffset, document);
          CharSequence indent = DocumentUtil.getIndent(document, lineStartOffset);
          document.insertString(lineStartOffset, indent + "# shellcheck disable=" + scCode + "\n");
        }

        @Override
        public boolean startInWriteAction() {
          return true;
        }
      });
    }
  }

  private void readShebang(@NotNull PsiFile file) {
    ASTNode shebang = file.getNode().findChildByType(BashTokenTypes.SHEBANG);
    if (shebang != null)
      shebangText = shebang.getText();
  }

  private int calcOffset(CharSequence sequence, int startOffset, int column) {
    int i = 1;
    while (i < column) {
      int c = Character.codePointAt(sequence, startOffset);
      i += c == '\t' ? 8 : 1;
      startOffset++;
    }
    return startOffset;
  }

  @NotNull
  private HighlightSeverity severity(@Nullable String level) {
    if ("error".equals(level)) {
      return HighlightSeverity.ERROR;
    }
    if ("warning".equals(level)) {
      return HighlightSeverity.WARNING;
    }
    return HighlightSeverity.WEAK_WARNING;
  }

  private void writeFileContentToStdin(@NotNull Process process, @NotNull String content, @NotNull Charset charset) throws IOException {
    try (OutputStream stdin = ObjectUtils.assertNotNull(process.getOutputStream())) {
      stdin.write(content.getBytes(charset));
      stdin.flush();
    }
    catch (IOException e) {
      throw new IOException("Failed to write file content to stdin\n\n" + content, e);
    }
  }

  class Result {
    int line;
    int endLine;
    int column;
    int endColumn;
    String level;
    String message;
    long code;
  }
}
