// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.patterns.PyElementPattern;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PyPathCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(PyPathCompletionContributor.class);
  private static final int STRING_LITERAL_LIMIT = 10_000;

  public PyPathCompletionContributor() {
    extend(CompletionType.BASIC,
           // For relative paths in project we use PythonPathReferenceContributor
           // This class is used only for absolute paths.
           pyStringLiteralMatches("^((/)|([A-Z]:((\\\\)|(/))))", SystemInfo.isFileSystemCaseSensitive ? Pattern.CASE_INSENSITIVE : 0),
           new PathCompletionProvider());
  }

  private static class PathCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PyStringLiteralExpression pos = getStringLiteral(parameters.getOriginalPosition());

      assert pos != null;

      Path path = Paths.get(pos.getStringValue());
      List<PyStringElement> stringElements = pos.getStringElements();
      stringElements.subList(0, stringElements.size() - 1);

      File f = path.toFile();
      final File dir = f.isDirectory() ? f : f.getParentFile();

      if (!dir.exists()) return;

      try {
        List<PathLookupElement> completions = ApplicationUtil.runWithCheckCanceled(() -> {
          File[] files = dir.listFiles();

          if (files == null) {
            return Collections.emptyList();
          }

          return ContainerUtil.map(files, file -> {
            String item = file.getPath().replace(File.separatorChar, '/');
            item = item.startsWith("/") ? item.substring(1) : item;
            return new PathLookupElement(item, file.isDirectory());
          });
        }, EmptyProgressIndicator.notNullize(ProgressManager.getInstance().getProgressIndicator()));

        result.addAllElements(completions);
      }
      catch (ProcessCanceledException e) {
        LOG.info("Path completion was canceled");
      }
      catch (Exception e) {
        LOG.info("Cannot add path completions", e);
      }
    }
  }

  public static PyElementPattern.Capture<PyStringLiteralExpression> pyStringLiteralMatches(final String regexp, int flags) {
    final Pattern pattern = Pattern.compile(regexp, flags);
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyStringLiteralExpression>(PyStringLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        final PyStringLiteralExpression expr = (PyStringLiteralExpression)o;
        // We complete only the last string element.
        if (!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT) {
          final String value = expr.getStringValue();
          return pattern.matcher(value).find();
        }
        return false;
      }
    });
  }

  @Nullable
  static PyStringLiteralExpression getStringLiteral(@Nullable PsiElement o) {
    return PsiTreeUtil.getContextOfType(o, PyStringLiteralExpression.class);
  }
}
