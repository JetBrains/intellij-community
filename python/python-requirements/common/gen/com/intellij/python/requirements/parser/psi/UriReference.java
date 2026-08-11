// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface UriReference extends PsiElement {

  @Nullable
  BzrLaunchpadUri getBzrLaunchpadUri();

  @Nullable
  EditableOption getEditableOption();

  @Nullable
  GitUri getGitUri();

  @Nullable
  QuotedMarker getQuotedMarker();

  @Nullable
  Uri getUri();

}
