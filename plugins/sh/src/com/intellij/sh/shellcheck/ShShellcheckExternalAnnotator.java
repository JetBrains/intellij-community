// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.ArrayUtil;
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

import static java.util.Arrays.asList;

public class ShShellcheckExternalAnnotator extends ExternalAnnotator<PsiFile, ShShellcheckExternalAnnotator.ShellcheckResponse> {
  private static final List<String> KNOWN_SHELLS = asList("bash", "dash", "ksh", "sh");
  private static final String DEFAULT_SHELL = "bash";

  private final static Logger LOG = Logger.getInstance(ShShellcheckExternalAnnotator.class);

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
  public ShellcheckResponse doAnnotate(@NotNull PsiFile file) {
    String shellcheckExecutable = ShSettings.getShellcheckPath();
    if (!ShShellcheckUtil.isValidPath(shellcheckExecutable)) return null;

    String fileContent = file.getText();
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine()
          .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
          .withExePath(shellcheckExecutable)
          .withParameters(getShellcheckExecutionParams(file));
      long timestamp = file.getModificationStamp();
      Process process = commandLine.createProcess();
      writeFileContentToStdin(process, fileContent, commandLine.getCharset());
      if (process.waitFor(10, TimeUnit.SECONDS)) {
        String output = StreamUtil.readText(process.getInputStream(), commandLine.getCharset());
        Type type = TypeToken.getParameterized(List.class, Result.class).getType();
        Collection<Result> results = new Gson().fromJson(output, type);
        return new ShellcheckResponse(results, timestamp);
      }
      return null;
    }
    catch (IOException | ExecutionException | InterruptedException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  public void apply(@NotNull PsiFile file, ShellcheckResponse shellcheckResponse, @NotNull AnnotationHolder holder) {
    super.apply(file, shellcheckResponse, holder);
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }
    for (Result result : shellcheckResponse.results) {
      CharSequence sequence = document.getCharsSequence();
      int startOffset = ShShellcheckUtil.calcOffset(sequence, document.getLineStartOffset(result.line - 1), result.column);
      int endOffset = ShShellcheckUtil.calcOffset(sequence, document.getLineStartOffset(result.endLine - 1), result.endColumn);
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

      String formattedMessage = format(message);
      Fix fix = result.fix;
      if (fix != null && !ArrayUtil.isEmpty(fix.replacements)) {
        annotation.registerFix(new QuickFixIntention(formattedMessage, fix, shellcheckResponse.timestamp));
      }
      String quotedMessage = quote(formattedMessage);
      annotation.registerFix(new SuppressInspectionIntention(quotedMessage, scCode, startOffset));
      annotation.registerFix(new DisableInspectionIntention(quotedMessage, scCode));
    }
  }

  @NotNull
  private static HighlightSeverity severity(@Nullable String level) {
    if ("error".equals(level)) {
      return HighlightSeverity.ERROR;
    }
    if ("warning".equals(level)) {
      return HighlightSeverity.WARNING;
    }
    return HighlightSeverity.WEAK_WARNING;
  }

  @NotNull
  private static List<String> getShellcheckExecutionParams(@NotNull PsiFile file) {
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
  private static String format(@NotNull String originalMessage) {
    return originalMessage.endsWith(".") ? originalMessage.substring(0, originalMessage.length() - 1) : originalMessage;
  }

  @NotNull
  private static String quote(@NotNull String originalMessage) {
    return "'" + StringUtil.first(originalMessage, 60, true) + "'";
  }

  @NotNull
  private static String getInterpreter(@NotNull PsiFile file) {
    if (!(file instanceof ShFile)) return DEFAULT_SHELL;
    return ShShebangParserUtil.getInterpreter((ShFile) file, KNOWN_SHELLS, DEFAULT_SHELL);
  }

  class ShellcheckResponse {
    final Collection<Result> results;
    final long timestamp;

    ShellcheckResponse(Collection<Result> results, long timestamp) {
      this.results = results;
      this.timestamp = timestamp;
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
    @Nullable Fix fix;
  }

  class Fix {
    Replacement[] replacements;
  }

  @SuppressWarnings("InnerClassMayBeStatic")
  class Replacement {
    int line;
    int column;
    int endLine;
    int endColumn;
    String replacement;
  }
}
