// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.codeInsight.generation.MemberChooserObjectBase;
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Serega.Vasiliev
 */
public final class GenerateDependencyUtil {
  private GenerateDependencyUtil() {
  }

  @NotNull
  public static List<MavenDomDependency> chooseDependencies(Collection<? extends MavenDomDependency> candidates, final Project project) {
    List<MavenDomDependency> dependencies = new ArrayList<>();

    MavenDomProjectModelMember[] memberCandidates =
      ContainerUtil.map2Array(candidates, MavenDomProjectModelMember.class, dependency -> new MavenDomProjectModelMember(dependency));
    MemberChooser<MavenDomProjectModelMember> chooser =
      new MemberChooser<MavenDomProjectModelMember>(memberCandidates, true, true, project) {
        @Override
        protected ShowContainersAction getShowContainersAction() {
          return new ShowContainersAction(MavenDomBundle.message("chooser.show.project.files"), MavenIcons.MavenProject);
        }

        @Override
        protected String getAllContainersNodeName() {
          return MavenDomBundle.message("all.dependencies");
        }
      };

    chooser.setTitle(MavenDomBundle.message("dependencies.chooser.title"));
    chooser.setCopyJavadocVisible(false);
    chooser.show();

    if (chooser.getExitCode() == MemberChooser.OK_EXIT_CODE) {
      final MavenDomProjectModelMember[] members = chooser.getSelectedElements(new MavenDomProjectModelMember[0]);
      if (members != null) {
        dependencies.addAll(ContainerUtil.mapNotNull(members, mavenDomProjectModelMember -> mavenDomProjectModelMember.getDependency()));
      }
    }

    return dependencies;
  }

  private static class MavenDomProjectModelMember extends MemberChooserObjectBase implements ClassMember {
    private final MavenDomDependency myDependency;

    MavenDomProjectModelMember(final MavenDomDependency dependency) {
      super(dependency.toString(), AllIcons.Nodes.PpLib);
      myDependency = dependency;
    }

    @NotNull
    @Override
    public String getText() {
      StringBuffer sb = new StringBuffer();

      append(sb, myDependency.getGroupId().getStringValue());
      append(sb, myDependency.getArtifactId().getStringValue());
      append(sb, myDependency.getVersion().getStringValue());

      return sb.toString();
    }

    private static void append(StringBuffer sb, String str) {
      if (!StringUtil.isEmptyOrSpaces(str)) {
        if (sb.length() > 0) sb.append(": ");
        sb.append(str);
      }
    }

    @Override
    public MemberChooserObject getParentNodeDelegate() {
      MavenDomDependency dependency = getDependency();

      return new MavenDomProjectModelFileMemberChooserObjectBase(dependency.getXmlTag().getContainingFile(),
                                                                 getProjectName(dependency));
    }

    @Nullable
    private static String getProjectName(@Nullable MavenDomDependency dependency) {
      if (dependency != null) {
        MavenDomProjectModel model = dependency.getParentOfType(MavenDomProjectModel.class, false);
        if (model != null) {
          String name = model.getName().getStringValue();
          return StringUtil.isEmptyOrSpaces(name) ? model.getArtifactId().getStringValue() : name;
        }
      }
      return null;
    }

    public MavenDomDependency getDependency() {
      return myDependency;
    }

    private static class MavenDomProjectModelFileMemberChooserObjectBase extends PsiElementMemberChooserObject {

      MavenDomProjectModelFileMemberChooserObjectBase(@NotNull final PsiFile psiFile, @Nullable String projectName) {
        super(psiFile, StringUtil.isEmptyOrSpaces(projectName) ? psiFile.getName() : projectName, MavenIcons.MavenProject);
      }
    }
  }
}
