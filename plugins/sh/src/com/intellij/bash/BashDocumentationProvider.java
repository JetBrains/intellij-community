package com.intellij.bash;

import com.intellij.bash.psi.BashGenericCommandDirective;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class BashDocumentationProvider extends AbstractDocumentationProvider {
  private static final int TIMEOUT_IN_MILLISECONDS = 3 * 1000;
  private final static Logger LOG = Logger.getInstance(BashDocumentationProvider.class);

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
    if (o instanceof LeafPsiElement && ((LeafPsiElement) o).getElementType() == BashTypes.WORD) {
      if (!(o.getParent() instanceof BashGenericCommandDirective)) return null;
      return wrapIntoHtml(fetchInfo(o.getText()));
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement) {
    return contextElement;
  }

  private final ConcurrentHashMap<String, String> myManCache = new ConcurrentHashMap<>();

  private String fetchInfo(@Nullable String commandName) {
    if (commandName == null) return null;
    String manExecutable = myManExecutable.getValue();
    if (manExecutable == null) return "Can't find info in your $PATH";

    return myManCache.computeIfAbsent(commandName, s -> {
      try {
        GeneralCommandLine commandLine = new GeneralCommandLine(manExecutable).withParameters(commandName);
        ProcessOutput output = ExecUtil.execAndGetOutput(commandLine, TIMEOUT_IN_MILLISECONDS);
        return output.getExitCode() != 0 ? output.getStderr() : output.getStdout();
      }
      catch (ExecutionException e) {
        LOG.warn(e);
        return null;
      }
    });
  }

  @Nullable
  private static String wrapIntoHtml(@Nullable String s) {
    return s == null ? null : "<html><body><pre>" + s + "</pre></body></html>";
  }
}
