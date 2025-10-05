// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.swing.*;
import java.io.IOException;
import java.util.Collections;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

public final class MavenFrameworkSupportProvider extends FrameworkSupportProvider {
  public MavenFrameworkSupportProvider() {
    super(MavenFrameworkSupportProvider.class.getName(), MavenProjectBundle.message("maven.name"));
  }

  @Override
  public @NotNull FrameworkSupportConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
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
          VirtualFile moduleFile = module.getModuleFile();
          if (moduleFile != null) {
            root = moduleFile.getParent();
            model.addContentEntry(root);
          }
          else {
            return;
          }
        }
        else {
          root = roots[0];
        }

        VirtualFile existingPom = null;
        for (VirtualFile child : root.getChildren()) {
          if (child.getName().startsWith("pom.")) {
            existingPom = child;
          }
        }

        if (existingPom != null) {
          MavenProjectsManager.getInstance(module.getProject()).addManagedFilesOrUnignore(Collections.singletonList(existingPom));
        }
        else {
          prepareProjectStructure(model, root);

          new MavenModuleBuilderHelper(new MavenId("groupId", module.getName(), "1.0-SNAPSHOT"), null, null, false, false, null,
                                       null, MavenProjectBundle.message("command.name.add.maven.support")).configure(model.getProject(), root, true);
        }
      }
    };
  }

  private static void prepareProjectStructure(@NotNull ModifiableRootModel model, @NotNull VirtualFile root) {
    VirtualFile src = root.findChild("src");
    if (src == null || !src.isDirectory()) return;

    if (ArrayUtil.contains(src, model.getSourceRoots())) {
      try {
        VirtualFile java = VfsUtil.createDirectories(src.getPath() + "/main/java");
        if (java != null && java.isDirectory()) {
          for (VirtualFile child : src.getChildren()) {
            if (!child.getName().equals("main")) {
              child.move(null, java);
            }
          }
        }
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }
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
    return moduleType.equals(JavaModuleType.getModuleType());
  }

  @Override
  public Icon getIcon() {
    return RepositoryLibraryLogo;
  }
}
