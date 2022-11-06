package com.jetbrains.performancePlugin.lang.doc;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ResourceUtil;
import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.lang.psi.IJPerfCommandName;
import com.jetbrains.performancePlugin.lang.psi.IJPerfPsiImplUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static com.intellij.openapi.util.Predicates.nonNull;

public class IJPerfDocumentationProvider extends AbstractDocumentationProvider {

  @Nullable
  @Override
  public @Nls String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    String title = null;
    if (!(element instanceof IJPerfCommandName)) {
      return null;
    }
    try {
      String commandDescriptionFileName = getCommandDescriptionFileName(IJPerfPsiImplUtil.getName((IJPerfCommandName)element));
      InputStream inputStream = getDocumentationStream(commandDescriptionFileName);
      if (inputStream != null) {
        title = ResourceUtil.loadText(inputStream);
        title = title.substring(title.indexOf("<title>") + "<title>".length(), title.indexOf("</title>"));
      }
    }
    catch (IOException ex) {
      Logger.getInstance(getClass()).error(ex.getMessage());
    }
    if (title != null) {
      title = DocumentationMarkup.DEFINITION_START + StringUtil.escapeXmlEntities(title) + DocumentationMarkup.DEFINITION_END;
    }
    return title;
  }

  private static InputStream getDocumentationStream(@NotNull String commandDescriptionFileName) {
    return CommandProvider.EP_NAME.getExtensionList().stream()
      .map(commandProvider -> ResourceUtil.getResourceAsStream(
        commandProvider.getClass().getClassLoader(), "commandDescriptions", commandDescriptionFileName))
      .filter(nonNull())
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static String getCommandDescriptionFileName(@Nullable String commandName) {
    return commandName != null ? commandName.replaceAll("%", "") + "Command.html" : null;
  }

  @Nullable
  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    String description;
    if (!(element instanceof IJPerfCommandName)) {
      return null;
    }
    try {
      String commandName = IJPerfPsiImplUtil.getName((IJPerfCommandName)element);
      String commandDescriptionFileName = getCommandDescriptionFileName(commandName);
      InputStream inputStream = getDocumentationStream(commandDescriptionFileName);
      if (inputStream != null) {
        description = ResourceUtil.loadText(inputStream);
        return DocumentationMarkup.DEFINITION_START + StringUtil.escapeXmlEntities(commandName) +
               DocumentationMarkup.DEFINITION_END +
               DocumentationMarkup.CONTENT_START + description +
               DocumentationMarkup.CONTENT_END;
      }
    }
    catch (IOException ex) {
      Logger.getInstance(getClass()).error(ex.getMessage());
    }
    return null;
  }
}
