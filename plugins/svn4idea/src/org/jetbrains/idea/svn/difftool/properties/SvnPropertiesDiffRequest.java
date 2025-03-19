// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnPropertiesDiffRequest extends ContentDiffRequest {
  private final @NotNull List<DiffContent> myContents;
  private final @NotNull List<String> myContentTitles;

  public SvnPropertiesDiffRequest(@Nullable List<PropertyData> before, @Nullable List<PropertyData> after,
                                  @Nullable String title1, @Nullable String title2) {
    assert before != null || after != null;

    myContents = Arrays.asList(createContent(before), createContent(after));
    myContentTitles = Arrays.asList(title1, title2);
  }

  public @NotNull DiffContent createContent(@Nullable List<PropertyData> content) {
    if (content == null) return new EmptyContent();

    return new PropertyContent(content);
  }

  @Override
  public @NotNull String getTitle() {
    return message("dialog.title.svn.properties.diff");
  }

  @Override
  public @NotNull List<String> getContentTitles() {
    return myContentTitles;
  }

  @Override
  public @NotNull List<DiffContent> getContents() {
    return myContents;
  }

  public static class PropertyContent extends DiffContentBase {
    private final @NotNull List<PropertyData> myProperties;

    public PropertyContent(@NotNull List<PropertyData> properties) {
      myProperties = properties;
    }

    public @NotNull List<PropertyData> getProperties() {
      return myProperties;
    }

    @Override
    public @Nullable FileType getContentType() {
      return null;
    }
  }
}
