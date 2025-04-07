// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.eel.provider.utils.EelPathUtils;
import com.intellij.platform.eel.provider.utils.EelPathUtils.FileTransferAttributesStrategy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.shellcheck.intention.ShDisableInspectionIntention;
import com.intellij.sh.shellcheck.intention.ShSuppressInspectionIntention;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.platform.eel.provider.EelNioBridgeServiceKt.asEelPath;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote;
import static java.util.Arrays.asList;

public class ShShellcheckExternalAnnotator
  extends ExternalAnnotator<ShShellcheckExternalAnnotator.CollectedInfo, ShShellcheckExternalAnnotator.ShellcheckResponse> {
  private static final Logger LOG = Logger.getInstance(ShShellcheckExternalAnnotator.class);
  private static final List<@NlsSafe String> KNOWN_SHELLS = asList("bash", "dash", "ksh", "sh"); //NON-NLS
  private static final @NlsSafe String DEFAULT_SHELL = "bash";
  private static final int TIMEOUT_IN_MILLISECONDS = 10_000;

  @Override
  public String getPairedBatchInspectionShortName() {
    return ShShellcheckInspection.SHORT_NAME;
  }

  @Override
  public @Nullable CollectedInfo collectInformation(@NotNull PsiFile file) {
    if (!(file instanceof ShFile)) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    VirtualFile parent = virtualFile.getParent();
    if (parent == null) return null;
    return new CollectedInfo(file.getProject(), parent.getPath(), file.getText(), file.getModificationStamp(),
                             getShellcheckExecutionParams(file));
  }

  @Override
  public @Nullable ShellcheckResponse doAnnotate(@NotNull CollectedInfo fileInfo) {
    // Temporary solution to avoid execution under read action in dumb mode. Should be removed after IDEA-229905 will be fixed
    Application application = ApplicationManager.getApplication();
    if (application != null && application.isReadAccessAllowed() && !application.isUnitTestMode()) return null;

    String shellcheckExecutable = ShSettings.getShellcheckPath(fileInfo.project);
    if (!ShShellcheckUtil.isExecutionValidPath(shellcheckExecutable)) return null;
    ShShellcheckUtil.checkShellCheckForUpdate(fileInfo.project);


    final var eelDescriptor = getEelDescriptor(fileInfo.project);

    try {
      FileTransferAttributesStrategy forceExecutePermission =
        FileTransferAttributesStrategy.copyWithRequiredPosixPermissions(PosixFilePermission.OWNER_EXECUTE);
      GeneralCommandLine commandLine = new GeneralCommandLine()
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        .withExePath(asEelPath(
          transferLocalContentToRemote(Path.of(shellcheckExecutable), new EelPathUtils.TransferTarget.Temporary(eelDescriptor), forceExecutePermission)).toString())
        .withParameters(fileInfo.executionParams);
      if (!ApplicationManager.getApplication().isUnitTestMode()) commandLine.withWorkDirectory(fileInfo.workDirectory);
      long timestamp = fileInfo.modificationStamp;
      OSProcessHandler handler = new OSProcessHandler(commandLine);
      Ref<ShellcheckResponse> response = Ref.create();
      handler.addProcessListener(new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          // The process ends up with code 1
          Type type = TypeToken.getParameterized(List.class, Result.class).getType();
          Collection<Result> results = new Gson().fromJson(getOutput().getStdout(), type);
          if (results != null) response.set(new ShellcheckResponse(results, timestamp));
        }
      });
      handler.startNotify();
      writeFileContentToStdin(handler.getProcess(), fileInfo.fileContent, commandLine.getCharset());
      if (!handler.waitFor(TIMEOUT_IN_MILLISECONDS)) {
        LOG.debug("Execution timeout, process will be forcibly terminated");
        handler.destroyProcess();
      }
      return response.get();
    }
    catch (ExecutionException e) {
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

    Collection<OuterLanguageElement> outerElements = PsiTreeUtil.findChildrenOfType(file, OuterLanguageElement.class);
    List<TextRange> rangesOfOuterElements = ContainerUtil.map(outerElements, el -> el.getTextRange());

    for (Result result : shellcheckResponse.results) {
      CharSequence sequence = document.getCharsSequence();
      int startOffset = ShShellcheckUtil.calcOffset(sequence, document.getLineStartOffset(result.line - 1), result.column);
      int endOffset = ShShellcheckUtil.calcOffset(sequence, document.getLineStartOffset(result.endLine - 1), result.endColumn);
      TextRange range = TextRange.create(startOffset, endOffset == startOffset ? endOffset + 1 : endOffset);

      // We skip results which out of scope for current file or intersect with outer language elements
      if (!file.getTextRange().contains(range) || ContainerUtil.exists(rangesOfOuterElements, it -> it.contains(range))) continue;

      long code = result.code;
      String message = result.message;
      @NonNls String scCode = "SC" + code;
      @NonNls String html =
        "<html>" +
        "<p>" + StringUtil.escapeXmlEntities(message) + "</p>" +
        "<p>See <a href='https://github.com/koalaman/shellcheck/wiki/SC" + code + "'>" + scCode + "</a>.</p>" +
        "</html>";
      AnnotationBuilder builder = holder.newAnnotation(severity(result.level), message).range(range).tooltip(html);

      String formattedMessage = format(message);
      Fix fix = result.fix;
      if (fix != null && !ArrayUtil.isEmpty(fix.replacements)) {
        builder = builder.withFix(new ShQuickFixIntention(formattedMessage, fix, shellcheckResponse.timestamp));
      }
      String quotedMessage = quote(formattedMessage);
      builder.withFix(new ShSuppressInspectionIntention(quotedMessage, scCode, startOffset))
        .withFix(new ShDisableInspectionIntention(quotedMessage, scCode))
        .create();
    }
  }

  private static @NotNull HighlightSeverity severity(@Nullable String level) {
    if ("error".equals(level)) {
      return HighlightSeverity.ERROR;
    }
    if ("warning".equals(level)) {
      return HighlightSeverity.WARNING;
    }
    return HighlightSeverity.WEAK_WARNING;
  }

  private static @NotNull List<@NlsSafe String> getShellcheckExecutionParams(@NotNull PsiFile file) {
    String interpreter = getInterpreter(file);
    List<String> params = new SmartList<>();
    ShShellcheckInspection inspection = ShShellcheckInspection.findShShellcheckInspection(file);

    Collections.addAll(params, "--color=never", "--format=json", "--severity=style", "--shell=" + interpreter, "--wiki-link-count=10",
                       //NON-NLS
                       "--exclude=SC1091", "-"); //NON-NLS
    inspection.getDisabledInspections().forEach(setting -> params.add("--exclude=" + setting));//NON-NLS
    return params;
  }

  private static void writeFileContentToStdin(@NotNull Process process, @NotNull String content, @NotNull Charset charset) {
    try (OutputStream stdin = Objects.requireNonNull(process.getOutputStream())) {
      stdin.write(content.getBytes(charset));
      stdin.flush();
    }
    catch (IOException e) {
      LOG.debug("Failed to write file content to stdin\n\n" + content, e);
    }
  }

  @Contract(pure = true)
  private static @NotNull String format(@NotNull String originalMessage) {
    return originalMessage.endsWith(".") ? originalMessage.substring(0, originalMessage.length() - 1) : originalMessage;
  }

  private static @NotNull String quote(@NotNull String originalMessage) {
    return "'" + StringUtil.first(originalMessage, 60, true) + "'";
  }

  private static @NotNull String getInterpreter(@NotNull PsiFile file) {
    if (!(file instanceof ShFile)) return DEFAULT_SHELL;
    return ShShebangParserUtil.getInterpreter((ShFile)file, KNOWN_SHELLS, DEFAULT_SHELL);
  }

  class ShellcheckResponse {
    final Collection<Result> results;
    final long timestamp;

    ShellcheckResponse(@NotNull Collection<Result> results, long timestamp) {
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
    @InspectionMessage String message;
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

  static class CollectedInfo {
    private final Project project;
    private final String workDirectory;
    private final String fileContent;
    private final long modificationStamp;
    private final List<String> executionParams;

    CollectedInfo(Project project, String workDirectory, String fileContent, long modificationStamp, List<String> executionParams) {
      this.project = project;
      this.workDirectory = workDirectory;
      this.fileContent = fileContent;
      this.modificationStamp = modificationStamp;
      this.executionParams = executionParams;
    }
  }
}
