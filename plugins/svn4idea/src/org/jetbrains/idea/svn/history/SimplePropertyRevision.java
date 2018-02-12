package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.List;

import static org.jetbrains.idea.svn.actions.ShowPropertiesDiffAction.toSortedStringPresentation;

public class SimplePropertyRevision implements ContentRevision, PropertyRevision {
  private final List<PropertyData> myProperty;
  private final FilePath myNewFilePath;
  private final String myRevision;

  @Nullable
  @Override
  public List<PropertyData> getProperties() {
    return myProperty;
  }

  public SimplePropertyRevision(final List<PropertyData> property, final FilePath newFilePath, final String revision) {
    myProperty = property;
    myNewFilePath = newFilePath;
    myRevision = revision;
  }

  @Nullable
  public String getContent() {
    return toSortedStringPresentation(myProperty);
  }

  @NotNull
  public FilePath getFile() {
    return myNewFilePath;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber() {
      public String asString() {
        return myRevision;
      }

      public int compareTo(final VcsRevisionNumber o) {
        return 0;
      }
    };
  }
}