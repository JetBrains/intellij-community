package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.List;

public class SvnPropertiesDiffRequest extends ContentDiffRequest {
  @NotNull private final List<DiffContent> myContents;
  @NotNull private final List<String> myContentTitles;
  @NotNull private final String myWindowTitle;

  public SvnPropertiesDiffRequest(@NotNull String windowTitle,
                                  @NotNull DiffContent content1,
                                  @NotNull DiffContent content2,
                                  @NotNull String title1,
                                  @NotNull String title2) {
    myWindowTitle = windowTitle;
    myContents = ContainerUtil.list(content1, content2);
    myContentTitles = ContainerUtil.list(title1, title2);

    assert content1 instanceof PropertyContent || content1 instanceof EmptyContent;
    assert content2 instanceof PropertyContent || content2 instanceof EmptyContent;
    assert content1 instanceof PropertyContent || content2 instanceof PropertyContent;
  }

  public SvnPropertiesDiffRequest(@Nullable List<PropertyData> before, @Nullable List<PropertyData> after,
                                  @Nullable String title1, @Nullable String title2) {
    assert before != null || after != null;

    myContents = ContainerUtil.list(createContent(before), createContent(after));
    myWindowTitle = "Svn Properties Diff";
    myContentTitles = ContainerUtil.list(title1, title2);
  }

  @NotNull
  public DiffContent createContent(@Nullable List<PropertyData> content) {
    if (content == null) return new EmptyContent();

    return new PropertyContent(content);
  }

  @NotNull
  @Override
  public String getTitle() {
    return myWindowTitle;
  }

  @NotNull
  @Override
  public List<String> getContentTitles() {
    return myContentTitles;
  }

  @NotNull
  @Override
  public List<DiffContent> getContents() {
    return myContents;
  }

  public static class PropertyContent extends DiffContentBase {
    @NotNull private final List<PropertyData> myProperties;

    public PropertyContent(@NotNull List<PropertyData> properties) {
      myProperties = properties;
    }

    @NotNull
    public List<PropertyData> getProperties() {
      return myProperties;
    }

    @Nullable
    @Override
    public FileType getContentType() {
      return null;
    }
  }
}
