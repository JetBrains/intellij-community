package com.intellij.sh.shellcheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.shellcheck.intention.DisableInspectionIntention;
import com.intellij.sh.shellcheck.intention.SuppressInspectionIntention;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ShShellcheckExternalAnnotator extends ExternalAnnotator<PsiFile, Collection<ShShellcheckExternalAnnotator.Result>> {
  private static final List<String> KNOWN_SHELLS = ContainerUtil.list("bash", "dash", "ksh", "sh");
  private static final String DEFAULT_SHELL = "bash";

  @Override
  public String getPairedBatchInspectionShortName() {
    return ShShellcheckInspection.SHORT_NAME;
  }

  @Nullable
  @Override
  public PsiFile collectInformation(@NotNull PsiFile file) {
    return file instanceof ShFile ? file : null;
  }

  @Nullable
  @Override
  public PsiFile collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(file);
  }

  @Nullable
  @Override
  public Collection<ShShellcheckExternalAnnotator.Result> doAnnotate(@NotNull PsiFile file) {
    String shellcheckExecutable = ShSettings.getInstance().getShellcheckPath();
    if (!ShShellcheckUtil.isValidPath(shellcheckExecutable)) return null;

    String fileContent = file.getText();
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine()
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withExePath(shellcheckExecutable)
          .withParameters(getShellcheckExecutionParams(file));
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
      Annotation annotation = holder.createAnnotation(severity(result.level), range, message, html);
      annotation.registerFix(new SuppressInspectionIntention(message, scCode, startOffset));
      annotation.registerFix(new DisableInspectionIntention(scCode));
    }
  }

  private static int calcOffset(CharSequence sequence, int startOffset, int column) {
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

  @NotNull
  private List<String> getShellcheckExecutionParams(@NotNull PsiFile file) {
    String interpreter = getInterpreter(file);
    List<String> params = ContainerUtil.newSmartList();
    ShShellcheckInspection inspection = ShShellcheckInspection.findShShellcheckInspection(file);

    Collections.addAll(params, "--color=never", "--format=json", "--severity=style", "--shell=" + interpreter, "--wiki-link-count=10", "--exclude=SC1091", "-");
    inspection.getDisabledInspections().forEach(setting -> params.add("--exclude=" + setting));
    return params;
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

  @NotNull
  private static String getInterpreter(@NotNull PsiFile file) {
    if (!(file instanceof ShFile)) return DEFAULT_SHELL;
    return ShShebangParserUtil.getInterpreter((ShFile) file, KNOWN_SHELLS, DEFAULT_SHELL);
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
