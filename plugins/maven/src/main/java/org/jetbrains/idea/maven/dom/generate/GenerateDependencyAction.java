/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencyManagement;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.List;
import java.util.Map;

public class GenerateDependencyAction extends GenerateDomElementAction {
  public GenerateDependencyAction() {
    super(new MavenGenerateProvider<MavenDomDependency>(MavenDomBundle.message("generate.dependency"), MavenDomDependency.class) {
        @Nullable
        @Override
        protected MavenDomDependency doGenerate(@NotNull final MavenDomProjectModel mavenModel, final Editor editor) {
          Project project = mavenModel.getManager().getProject();

          final Map<DependencyConflictId, MavenDomDependency> managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(mavenModel);

          final List<MavenId> ids = MavenArtifactSearchDialog.searchForArtifact(project, managedDependencies.values());
          if (ids.isEmpty()) return null;

          PsiDocumentManager.getInstance(project).commitAllDocuments();

          XmlFile psiFile = DomUtil.getFile(mavenModel);
          return WriteCommandAction.writeCommandAction(psiFile.getProject(), psiFile).withName("Generate Dependency").compute(() -> {
              boolean isInsideManagedDependencies;

              MavenDomDependencyManagement dependencyManagement = mavenModel.getDependencyManagement();
              XmlElement managedDependencyXml = dependencyManagement.getXmlElement();
              if (managedDependencyXml != null && managedDependencyXml.getTextRange().contains(editor.getCaretModel().getOffset())) {
                isInsideManagedDependencies = true;
              }
              else {
                isInsideManagedDependencies = false;
              }

              for (MavenId each : ids) {
                MavenDomDependency res;
                if (isInsideManagedDependencies) {
                  res = MavenDomUtil.createDomDependency(dependencyManagement.getDependencies(), editor, each);
                }
                else {
                  DependencyConflictId conflictId = new DependencyConflictId(each.getGroupId(), each.getArtifactId(), null, null);
                  MavenDomDependency managedDependenciesDom = managedDependencies.get(conflictId);

                  if (managedDependenciesDom != null
                      && Comparing.equal(each.getVersion(), managedDependenciesDom.getVersion().getStringValue())) {
                    // Generate dependency without <version> tag
                    res = MavenDomUtil.createDomDependency(mavenModel.getDependencies(), editor);

                    res.getGroupId().setStringValue(conflictId.getGroupId());
                    res.getArtifactId().setStringValue(conflictId.getArtifactId());
                  }
                  else {
                    res = MavenDomUtil.createDomDependency(mavenModel.getDependencies(), editor, each);
                  }
                }
                return (res);
              }
              return null;
            });
        }
      }, AllIcons.Nodes.PpLib);
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }
}
