// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.plugins.api.MavenSoftAwareReferenceProvider;
import org.jetbrains.idea.reposearch.DependencySearchService;

/**
 * Adds references to string like "groupId:artifactId:version"
 * @author Sergey Evdokimov
 */
public class MavenDependencyReferenceProvider extends PsiReferenceProvider implements MavenSoftAwareReferenceProvider {

  private boolean mySoft = true;

  private boolean myCanHasVersion = true;

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    TextRange range = ElementManipulators.getValueTextRange(element);

    String text = range.substring(element.getText());

    int firstDelim = text.indexOf(':');

    if (firstDelim == -1) {
      return new PsiReference[]{
        new GroupReference(element, range, mySoft)
      };
    }

    int secondDelim = myCanHasVersion ? text.indexOf(':', firstDelim + 1) : -1;

    int start = range.getStartOffset();

    if (secondDelim == -1) {
      return new PsiReference[]{
        new GroupReference(element, new TextRange(start, start + firstDelim), mySoft),
        new ArtifactReference(text.substring(0, firstDelim),
                              element, new TextRange(start + firstDelim + 1, range.getEndOffset()), mySoft)
      };
    }

    int lastDelim = text.indexOf(':',secondDelim + 1);
    if (lastDelim == -1) {
      lastDelim = text.length();
    }

    return new PsiReference[]{
      new GroupReference(element, new TextRange(start, start + firstDelim), mySoft),

      new ArtifactReference(text.substring(0, firstDelim),
                            element, new TextRange(start + firstDelim + 1, start + secondDelim), mySoft),

      new VersionReference(text.substring(0, firstDelim), text.substring(firstDelim + 1, secondDelim),
                           element, new TextRange(start + secondDelim + 1, start + lastDelim), mySoft)
    };
  }

  @Override
  public void setSoft(boolean soft) {
    mySoft = soft;
  }

  public boolean isCanHasVersion() {
    return myCanHasVersion;
  }

  public void setCanHasVersion(boolean canHasVersion) {
    myCanHasVersion = canHasVersion;
  }

  private static class GroupReference extends PsiReferenceBase<PsiElement> {

    GroupReference(PsiElement element, TextRange range, boolean soft) {
      super(element, range, soft);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
      return DependencySearchService.getInstance(getElement().getProject()).getGroupIds("").toArray();
    }
  }

  public static class ArtifactReference extends PsiReferenceBase<PsiElement> {

    private final String myGroupId;

    public ArtifactReference(@NotNull String groupId, @NotNull PsiElement element, @NotNull TextRange range, @NotNull boolean soft) {
      super(element, range, soft);
      myGroupId = groupId;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
      if (StringUtil.isEmptyOrSpaces(myGroupId)) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

      return DependencySearchService.getInstance(getElement().getProject()).getArtifactIds(myGroupId).toArray();
    }
  }

  public static class VersionReference extends PsiReferenceBase<PsiElement> {

    private final String myGroupId;
    private final String myArtifactId;

    public VersionReference(@NotNull String groupId, @NotNull String artifactId, @NotNull PsiElement element, @NotNull TextRange range, @NotNull boolean soft) {
      super(element, range, soft);
      myGroupId = groupId;
      myArtifactId = artifactId;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
      if (StringUtil.isEmptyOrSpaces(myGroupId) || StringUtil.isEmptyOrSpaces(myArtifactId)) {
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }
      return DependencySearchService.getInstance(getElement().getProject()).getVersions(myGroupId, myArtifactId).toArray();
    }
  }
}
