// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.psi.impl.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public final class MarkdownPsiElementFactory {
  private MarkdownPsiElementFactory() { }

  public static @NotNull MarkdownFile createFile(@NotNull Project project, @NotNull String text) {
    final LightVirtualFile virtualFile = new LightVirtualFile("temp.rb", MarkdownLanguage.INSTANCE, text);
    PsiFile psiFile = ((PsiFileFactoryImpl)PsiFileFactory.getInstance(project))
      .trySetupPsiForFile(virtualFile, MarkdownLanguage.INSTANCE, true, true);

    if (!(psiFile instanceof MarkdownFile)) {
      throw new RuntimeException("Cannot create a new markdown file. Text: " + text);
    }

    return (MarkdownFile)psiFile;
  }


  public static @NotNull MarkdownCodeFence createCodeFence(@NotNull Project project, @Nullable String language, @NotNull String text) {
    return createCodeFence(project, language, text, null);
  }


  public static @NotNull MarkdownCodeFence createCodeFence(@NotNull Project project,
                                                           @Nullable String language,
                                                           @NotNull String text,
                                                           @Nullable String indent) {
    String content = "```" + StringUtil.notNullize(language) + "\n" +
                     text + "\n" +
                     StringUtil.notNullize(indent) + "```";

    final MarkdownFile file = createFile(project, content);

    return (MarkdownCodeFence)file.getFirstChild();
  }

  public static @NotNull MarkdownPsiElement createTextElement(@NotNull Project project, @NotNull String text) {
    return (MarkdownPsiElement)createFile(project, text).getFirstChild();
  }

  public static @NotNull MarkdownHeader createSetext(@NotNull Project project, @NotNull String text, @NotNull String symbol, int count) {
    return (MarkdownHeader)createFile(project, text + "\n" + StringUtil.repeat(symbol, count)).getFirstChild();
  }

  public static @NotNull MarkdownHeader createHeader(@NotNull Project project, @NotNull String text, int level) {
    return (MarkdownHeader)createFile(project, StringUtil.repeat("#", level) + " " + text).getFirstChild();
  }

  public static @NotNull MarkdownHeader createHeader(@NotNull Project project, @NotNull String text) {
    return (MarkdownHeader)createFile(project, text).getFirstChild();
  }

  public static @NotNull PsiElement createNewLine(@NotNull Project project) {
    return createFile(project, "\n").getFirstChild();
  }

  /**
   * Prepares markdown file with [num] of new lines.
   *
   * @return root element children of which are new lines
   */
  public static @NotNull MarkdownFile createNewLines(@NotNull Project project, int num) {
    return createFile(project, StringUtil.repeat("\n", num));
  }

  /**
   * Returns pair of the link reference and its declaration
   */
  public static @NotNull Pair<PsiElement, PsiElement> createLinkDeclarationAndReference(@NotNull Project project,
                                                                               @NotNull String url,
                                                                               @NotNull String text,
                                                                               @Nullable String title,
                                                                               @NotNull String reference) {
    text = ObjectUtils.notNull(text, reference);
    title = title == null ? "" : " " + title;

    String linkReference = "[" + text + "][" + reference + "]" + "\n\n" + "[" + reference + "]" + ": " + url + title;

    PsiElement linkReferenceElement = createFile(project, linkReference);

    PsiElement ref = linkReferenceElement.getFirstChild();
    assert ref instanceof MarkdownParagraph;

    PsiElement declaration = linkReferenceElement.getLastChild();
    assert declaration instanceof MarkdownParagraph || declaration instanceof MarkdownLinkDefinition;

    return Pair.create(ref, declaration);
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownTableSeparatorRow createTableSeparatorRow(@NotNull Project project, @NotNull String text) {
    final var columnsCount = StringUtil.countChars(text, '|') - 1;
    if (columnsCount < 1) {
      throw new IllegalArgumentException("Passed separator text should be valid and contain at least one column.\n Text passed: [" + text + "]");
    }
    String markdownFile = "|" + "    |".repeat(columnsCount) + '\n' + text;
    final var file = createFile(project, markdownFile);
    final var table = PsiTreeUtil.getParentOfType(file.findElementAt(0), MarkdownTable.class);
    if (table == null) {
      final var psi = DebugUtil.psiToString(file, true, true);
      throw new IllegalStateException("Failed to find table. PSI:\n" + psi);
    }
    final var separatorRowElement = PsiTreeUtil.getChildOfType(table, MarkdownTableSeparatorRow.class);
    if (separatorRowElement == null) {
      final var psi = DebugUtil.psiToString(file, true, true);
      throw new IllegalStateException("Failed to find separator row. PSI:\n" + psi);
    }
    return separatorRowElement;
  }

  @ApiStatus.Experimental
  public static @NotNull Pair<MarkdownTableCell, PsiElement> createTableCell(@NotNull Project project, @NotNull String text) {
    final var content = "|" + text + "|\n|----|";
    final var file = createFile(project, content);
    final var contentElement = file.findElementAt(1);
    final var cell = Objects.requireNonNull(PsiTreeUtil.getParentOfType(contentElement, MarkdownTableCell.class));
    final var separator = cell.getNextSibling();
    return new Pair<>(cell, separator);
  }

  private static @NotNull MarkdownTable findTable(@NotNull PsiElement element) {
    return Objects.requireNonNull(PsiTreeUtil.getParentOfType(element, MarkdownTable.class));
  }

  @ApiStatus.Experimental
  public static @NotNull PsiElement createTableSeparator(@NotNull Project project) {
    final var content = "|    |\n|----|";
    final var file = createFile(project, content);
    return Objects.requireNonNull(file.findElementAt(0));
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownTableRow createTableRow(@NotNull Project project, @NotNull Collection<String> contents) {
    final var builder = new StringBuilder();
    builder.append('|');
    //noinspection StringRepeatCanBeUsed
    for (int count = 0; count < contents.size(); count += 1) {
      builder.append("     |");
    }
    builder.append('\n');
    builder.append('|');
    //noinspection StringRepeatCanBeUsed
    for (int count = 0; count < contents.size(); count += 1) {
      builder.append("-----|");
    }
    builder.append('\n');
    builder.append('|');
    for (var content : contents) {
      builder.append(content);
      builder.append('|');
    }
    builder.append('\n');
    builder.append('|');
    //noinspection StringRepeatCanBeUsed
    for (int count = 0; count < contents.size(); count += 1) {
      builder.append("     |");
    }
    builder.append('\n');
    final var file = createFile(project, builder.toString());
    final var element = Objects.requireNonNull(file.findElementAt(0));
    final var row = Objects.requireNonNull(findTable(element).getLastChild().getPrevSibling().getPrevSibling());
    if (row instanceof MarkdownTableRow) {
      return (MarkdownTableRow)row;
    } else {
      throw new IllegalStateException("Failed to find row element");
    }
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownTableRow createTableEmptyRow(@NotNull Project project, @NotNull Collection<Integer> widths) {
    final var contents = ContainerUtil.map(widths, width -> " ".repeat(width));
    return createTableRow(project, contents);
  }

  @ApiStatus.Experimental
  public static @NotNull PsiElement createBlockQuoteArrow(@NotNull Project project) {
    final var contents = "> ";
    final var file = createFile(project, contents);
    return Objects.requireNonNull(file.findElementAt(1));
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownHeader createHeader(@NotNull Project project, int level, @NotNull String text) {
    final var contents = StringUtil.repeat("#", level) + " " + text;
    final var file = createFile(project, contents);
    final var element = Objects.requireNonNull(file.getFirstChild());
    assert(element instanceof MarkdownHeader);
    return (MarkdownHeader)element;
  }

  @ApiStatus.Experimental
  public static @NotNull PsiElement createListMarker(@NotNull Project project, @NotNull String markerText) {
    final var contents = markerText + " list item";
    final var file = createFile(project, contents);
    return Objects.requireNonNull(file.getFirstChild().getFirstChild().getFirstChild());
  }

  @ApiStatus.Experimental
  public static @NotNull Pair<PsiElement, PsiElement> createListMarkerWithCheckbox(@NotNull Project project, @NotNull String markerText, boolean checked) {
    var text = markerText;
    if (checked) {
      text += " [x]";
    } else {
      text += " [ ]";
    }
    final var marker = createListMarker(project, text);
    final var checkbox = Objects.requireNonNull(marker.getNextSibling());
    return new Pair<>(marker, checkbox);
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownList createEmptyList(@NotNull Project project, boolean ordered) {
    final String contents;
    if (ordered) {
      contents = "1) list item";
    } else {
      contents = "* list item";
    }
    final var file = createFile(project, contents);
    final var list = Objects.requireNonNull(file.getFirstChild());
    assert(list instanceof MarkdownList);
    for (final var child: list.getChildren()) {
      child.delete();
    }
    return (MarkdownList)list;
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownList createList(
    @NotNull Project project,
    @NotNull Iterable<@NotNull String> items,
    @NotNull Function<? super Integer, String> markerSupplier
  ) {
    final var builder = new StringBuilder();
    var itemIndex = 0;
    for (final var item: items) {
      builder.append(markerSupplier.apply(itemIndex));
      builder.append(" ");
      builder.append(item);
      builder.append("\n");
      itemIndex += 1;
    }
    final var file = createFile(project, builder.toString());
    final var list = Objects.requireNonNull(file.getFirstChild());
    assert(list instanceof MarkdownList);
    return (MarkdownList)list;
  }

  @ApiStatus.Experimental
  public static @NotNull MarkdownLinkDestination createLinkDestination(@NotNull Project project, @NotNull String link) {
    final var content = "[](" + link + ")";
    final var file = createFile(project, content);
    final var element = Objects.requireNonNull(file.getFirstChild().getFirstChild());
    if (!(element instanceof MarkdownInlineLink)) {
      final var psi = DebugUtil.psiToString(file, true, true);
      final var message = "Expected a MarkdownInlineLink but was " + element + ". PSI was:\n" + psi;
      throw new IllegalStateException(message);
    }
    final var destination = Objects.requireNonNull(
      ((MarkdownInlineLink)element).getLinkDestination(),
      () -> "Failed to get link destination. PSI was:\n" + DebugUtil.psiToString(file, true, true)
    );
    return destination;
  }
}
