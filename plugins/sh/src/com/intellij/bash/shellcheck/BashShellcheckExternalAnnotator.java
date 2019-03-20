package com.intellij.bash.shellcheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.bash.psi.BashFile;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
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
  class Result {
    int line;
    int endLine;
    int column;
    int endColumn;
    String level;
    String message;
    long code;
  }

  @Nullable
  @Override
  public String collectInformation(@NotNull PsiFile file) {
    return file instanceof BashFile ? file.getText() : null;
  }

  @Nullable
  @Override
  public String collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return editor.getDocument().getText();
  }

  @Nullable
  @Override
  public Collection<BashShellcheckExternalAnnotator.Result> doAnnotate(String fileContent) {
    String shellcheckExecutable = Registry.stringValue("bash.shellcheck.path");
    if (StringUtil.isEmpty(shellcheckExecutable)) {
      return null;
    }
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine()
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withExePath(shellcheckExecutable)
          .withParameters("--color=never", "--format=json", "--severity=style", "--shell=bash", "--wiki-link-count=10", "-");
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
      int startOffset = document.getLineStartOffset(result.line - 1) + result.column - 1;
      int endOffset = document.getLineStartOffset(result.endLine - 1) + result.endColumn - 1;
      TextRange range = TextRange.create(startOffset, endOffset == startOffset ? endOffset + 1 : endOffset);
      long code = result.code;
      String message = result.message;
      holder.createAnnotation(severity(result.level), range,
          message,
          "<html>" + StringUtil.escapeXml(message) + " " +
              "See <a href='https://github.com/koalaman/shellcheck/wiki/SC" + code + "'>SC" + code + "</a>." +
              "</html>");
    }
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

  @Override
  public String getPairedBatchInspectionShortName() {
    return BashShellcheckInspection.SHORT_NAME;
  }

  private static void writeFileContentToStdin(@NotNull Process process, @NotNull String content, @NotNull Charset charset) throws IOException {
    try (OutputStream stdin = ObjectUtils.assertNotNull(process.getOutputStream())) {
      stdin.write(content.getBytes(charset));
      stdin.flush();
    }
    catch (IOException e) {
      throw new IOException("Failed to write file content to stdin\n\n" + content, e);
    }
  }
}
