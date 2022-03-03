// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.sh.psi.ShGenericCommandDirective;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;
import static com.intellij.sh.statistics.ShCounterUsagesCollector.DOCUMENTATION_PROVIDER_USED_EVENT_ID;

final class ShDocumentationProvider implements DocumentationProvider {
  private static final int TIMEOUT_IN_MILLISECONDS = 3 * 1000;
  private final static Logger LOG = Logger.getInstance(ShDocumentationProvider.class);

  private static final NullableLazyValue<String> MAN_EXECUTABLE = atomicLazyNullable(() -> {
    String path = EnvironmentUtil.getValue("PATH");
    if (path != null) {
      for (String dir : StringUtil.tokenize(path, File.pathSeparator)) {
        File file = new File(dir, "info");
        if (file.canExecute()) return file.getAbsolutePath();
      }
    }
    return null;
  });

  @Override
  public @NlsSafe String generateDoc(PsiElement o, PsiElement originalElement) {
    if (!wordWithDocumentation(o)) return null;

    DOCUMENTATION_PROVIDER_USED_EVENT_ID.log();
    return wrapIntoHtml(fetchInfo(o.getText()));
  }

  private static boolean wordWithDocumentation(@Nullable PsiElement o) {
    return o instanceof LeafPsiElement
        && ((LeafPsiElement) o).getElementType() == ShTypes.WORD
        && (o.getParent() instanceof ShLiteral)
        && (o.getParent().getParent() instanceof ShGenericCommandDirective);
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement,
                                                  int targetOffset) {
    ASTNode node = contextElement == null ? null : contextElement.getNode();
    if (node == null || (TreeUtil.isWhitespaceOrComment(node) || node.getElementType() == ShTypes.LINEFEED)) {
      PsiElement at = targetOffset > 0 ? file.findElementAt(targetOffset - 1) : null;
      if (wordWithDocumentation(at)) return at;
    }
    return contextElement;
  }

  private final ConcurrentHashMap<String, String> myManCache = new ConcurrentHashMap<>();

  private @NlsSafe String fetchInfo(@Nullable String commandName) {
    if (commandName == null) return null;
    String manExecutable = MAN_EXECUTABLE.getValue();
    if (manExecutable == null) return ShBundle.message("error.message.can.t.find.info.in.your.path");

    return myManCache.computeIfAbsent(commandName, s -> {
      try {
        return ApplicationUtil.runWithCheckCanceled(() -> {
          GeneralCommandLine commandLine = new GeneralCommandLine(manExecutable).withParameters(commandName);
          ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_IN_MILLISECONDS);
          return output.getExitCode() != 0 ? output.getStderr() : output.getStdout();
        }, ProgressManager.getInstance().getProgressIndicator());
      }
      catch (ProcessCanceledException e) { throw  e; }
      catch (Exception e) {
        LOG.warn(e);
        return null;
      }
    });
  }

  @Nullable
  private static String wrapIntoHtml(@Nullable String s) {
    if (s == null) return null;

    @NonNls StringBuilder sb = new StringBuilder("<html><body><pre>");
    try {
      @NonNls Matcher m = URLUtil.URL_PATTERN.matcher(StringUtil.escapeXmlEntities(s));
      while (m.find()) {
        if (m.groupCount() > 0) {
          String url = m.group(0);
          m.appendReplacement(sb, HtmlChunk.link(url, url).toString());
        }
      }
      m.appendTail(sb);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    sb.append("</pre></body></html>");
    return sb.toString();
  }
}
