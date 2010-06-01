/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenIcons;

import javax.swing.*;
import java.util.Collections;

public class MavenFrameworkSupportProvider extends FrameworkSupportProvider {
  public MavenFrameworkSupportProvider() {
    super(MavenFrameworkSupportProvider.class.getName(), "Maven");
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    return new FrameworkSupportConfigurable() {
      @Override
      public JComponent getComponent() {
        return new JPanel();
      }

      @Override
      public void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, @Nullable Library library) {
        VirtualFile[] roots = model.getContentRoots();
        VirtualFile root;
        if (roots.length == 0) {
          root = module.getModuleFile().getParent();
          model.addContentEntry(root);
        }
        else {
          root = roots[0];
        }

        VirtualFile existingPom = root.findChild(MavenConstants.POM_XML);
        if (existingPom != null) {
          MavenProjectsManager.getInstance(module.getProject()).addManagedFiles(Collections.singletonList(existingPom));
        }
        else {
          new MavenModuleBuilderHelper(new MavenId("groupId", module.getName(), "1.0-SNAPSHOT"), null, null, false, false, null,
                                       "Add Maven Support").configure(model.getProject(), root, true);
        }
      }
    };
  }

  @Override
  public boolean isEnabledForModuleBuilder(@NotNull ModuleBuilder builder) {
    return false;
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module) {
    return MavenProjectsManager.getInstance(module.getProject()).isMavenizedModule(module);
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return moduleType.equals(StdModuleTypes.JAVA);
  }

  @Override
  public Icon getIcon() {
    return MavenIcons.MAVEN_ICON;
  }
}
