package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.plugins.api.MavenSoftAwareReferenceProvider;

/**
 * Adds references to string like "groupId:artifactId:version"
 * @author Sergey Evdokimov
 */
public class MavenDependencyReferenceProvider extends PsiReferenceProvider implements MavenSoftAwareReferenceProvider {

  private boolean mySoft = true;

  private boolean myCanHasVersion = true;

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    TextRange range = manipulator.getRangeInElement(element);

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

    int lastDelim = text.indexOf(secondDelim + 1);
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

    public GroupReference(PsiElement element, TextRange range, boolean soft) {
      super(element, range, soft);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return MavenProjectIndicesManager.getInstance(getElement().getProject()).getGroupIds().toArray();
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

    @NotNull
    @Override
    public Object[] getVariants() {
      if (StringUtil.isEmptyOrSpaces(myGroupId)) return ArrayUtil.EMPTY_OBJECT_ARRAY;

      MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(getElement().getProject());
      return manager.getArtifactIds(myGroupId).toArray();
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

    @NotNull
    @Override
    public Object[] getVariants() {
      if (StringUtil.isEmptyOrSpaces(myGroupId) || StringUtil.isEmptyOrSpaces(myArtifactId)) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(getElement().getProject());
      return manager.getVersions(myGroupId, myArtifactId).toArray();
    }
  }
}
