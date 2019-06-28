// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.sh.psi.ShGenericCommandDirective;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class ShDocumentationProvider extends AbstractDocumentationProvider {
  private static final int TIMEOUT_IN_MILLISECONDS = 3 * 1000;
  private final static Logger LOG = Logger.getInstance(ShDocumentationProvider.class);
  private static final String FEATURE_ACTION_ID = "DocumentationProviderUsed";

  private static final NullableLazyValue<String> myManExecutable = new AtomicNullableLazyValue<String>() {
    @Nullable
    @Override
    protected String compute() {
      String path = EnvironmentUtil.getValue("PATH");
      if (path != null) {
        for (String dir : StringUtil.tokenize(path, File.pathSeparator)) {
          File file = new File(dir, "info");
          if (file.canExecute()) return file.getAbsolutePath();
        }
      }
      return null;
    }
  };

  @Override
  public String generateDoc(PsiElement o, PsiElement originalElement) {
    if (!wordWithDocumentation(o)) return null;

    ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
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
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement) {
    ASTNode node = contextElement == null ? null : contextElement.getNode();
    if (node == null || (TreeUtil.isWhitespaceOrComment(node) || node.getElementType() == ShTypes.LINEFEED)) {
      int offset = editor.getCaretModel().getPrimaryCaret().getOffset();
      PsiElement at = offset > 0 ? file.findElementAt(offset - 1) : null;
      if (wordWithDocumentation(at)) return at;
    }
    return contextElement;
  }

  private final ConcurrentHashMap<String, String> myManCache = new ConcurrentHashMap<>();

  private String fetchInfo(@Nullable String commandName) {
    if (commandName == null) return null;
    String manExecutable = myManExecutable.getValue();
    if (manExecutable == null) return "Can't find info in your $PATH";

    return myManCache.computeIfAbsent(commandName, s -> {
      try {
        return ApplicationManager.getApplication().executeOnPooledThread(() -> {
          GeneralCommandLine commandLine = new GeneralCommandLine(manExecutable).withParameters(commandName);
          ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_IN_MILLISECONDS);
          return output.getExitCode() != 0 ? output.getStderr() : output.getStdout();
        }).get(TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) {
        LOG.warn(e);
        return null;
      }
    });
  }

  @Nullable
  private static String wrapIntoHtml(@Nullable String s) {
    if (s == null) return null;

    StringBuffer sb = new StringBuffer("<html><body><pre>");
    try {
      Matcher m = URLUtil.URL_PATTERN.matcher(StringUtil.escapeXmlEntities(s));
      while (m.find()) {
        if (m.groupCount() > 0) {
          String url = m.group(0);
          m.appendReplacement(sb, "<a href='" + url + "'>" + url + "</a>");
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
