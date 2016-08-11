/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.project.model.impl.module.JpsRootModel;
import com.intellij.project.model.impl.module.content.JpsContentEntry;
import com.intellij.project.model.impl.module.content.JpsSourceFolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.Url;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 4/30/2014
 */
public class MavenSourceFoldersModuleExtension extends ModuleExtension<MavenSourceFoldersModuleExtension> {

  private ModifiableRootModel myRootModel;
  private JpsModule myDummyJpsModule;
  private JpsRootModel myDummyJpsRootModel;
  private final Set<JpsSourceFolder> myJpsSourceFolders = new TreeSet<>(ContentFolderComparator.INSTANCE);
  private boolean isJpsSourceFoldersChanged;

  public void init(@NotNull Module module, @NotNull ModifiableRootModel modifiableRootModel) {
    myRootModel = modifiableRootModel;

    myDummyJpsModule = JpsElementFactory.getInstance()
      .createModule(module.getName(), JpsJavaModuleType.INSTANCE, JpsElementFactory.getInstance().createDummyElement());
    myDummyJpsRootModel = new JpsRootModel(module, myDummyJpsModule);

    for (JpsSourceFolder folder : myJpsSourceFolders) {
      Disposer.dispose(folder);
    }
    myJpsSourceFolders.clear();

    for (ContentEntry eachEntry : modifiableRootModel.getContentEntries()) {
      for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
        //noinspection unchecked
        final JpsModuleSourceRoot jpsModuleSourceRoot =
          JpsElementFactory.getInstance().createModuleSourceRoot(
            eachFolder.getUrl(),
            (JpsModuleSourceRootType<JpsElement>)eachFolder.getRootType(),
            eachFolder.getJpsElement().getProperties().getBulkModificationSupport().createCopy());

        addJspSourceFolder(jpsModuleSourceRoot, eachFolder.getUrl());
      }
    }
  }

  @Override
  public ModuleExtension getModifiableModel(boolean writable) {
    return new MavenSourceFoldersModuleExtension();
  }

  @Override
  public void commit() {
    if (!isJpsSourceFoldersChanged) return;

    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
        boolean found = false;
        for (JpsSourceFolder jpsSourceFolder : myJpsSourceFolders) {
          if (StringUtil.equals(jpsSourceFolder.getUrl(), eachFolder.getUrl())
              && eachFolder.getRootType().equals(jpsSourceFolder.getRootType())) {
            found = true;
            if (eachFolder.getRootType() instanceof JavaSourceRootType) {
              final JavaSourceRootProperties jpsJavaSourceRootProperties =
                jpsSourceFolder.getJpsElement().getProperties((JavaSourceRootType)eachFolder.getRootType());

              final JavaSourceRootProperties javaSourceRootProperties =
                eachFolder.getJpsElement().getProperties((JavaSourceRootType)eachFolder.getRootType());

              if (javaSourceRootProperties != null && jpsJavaSourceRootProperties != null) {
                javaSourceRootProperties.applyChanges(jpsJavaSourceRootProperties);
              }
            }

            myJpsSourceFolders.remove(jpsSourceFolder);
            Disposer.dispose(jpsSourceFolder);
            break;
          }
        }
        if (!found) {
          eachEntry.removeSourceFolder(eachFolder);
        }
      }
    }

    for (JpsSourceFolder jpsSourceFolder : myJpsSourceFolders) {
      Url url = new Url(jpsSourceFolder.getUrl());
      ContentEntry e = getContentRootFor(url);
      if (e == null) continue;
      //noinspection unchecked
      JpsModuleSourceRootType<JpsElement> sourceRootType = (JpsModuleSourceRootType<JpsElement>)jpsSourceFolder.getRootType();
      final JpsElementBase properties = (JpsElementBase)jpsSourceFolder.getSourceRoot().getProperties();
      //noinspection unchecked
      properties.setParent(null);
      e.addSourceFolder(url.getUrl(), sourceRootType, properties);
    }

    isJpsSourceFoldersChanged = false;
  }

  @Override
  public boolean isChanged() {
    return isJpsSourceFoldersChanged;
  }

  @Override
  public void dispose() {
    for (JpsSourceFolder folder : myJpsSourceFolders) {
      Disposer.dispose(folder);
    }
    myJpsSourceFolders.clear();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
  }

  public void clearSourceFolders() {
    for (JpsSourceFolder folder : myJpsSourceFolders) {
      Disposer.dispose(folder);
    }
    myJpsSourceFolders.clear();
    isJpsSourceFoldersChanged = true;
  }

  public <P extends JpsElement> void addSourceFolder(final @NotNull Url url,
                                                     final @NotNull JpsModuleSourceRootType<P> rootType,
                                                     final @NotNull P properties) {
    for (Iterator<JpsSourceFolder> iterator = myJpsSourceFolders.iterator(); iterator.hasNext(); ) {
      JpsSourceFolder eachFolder = iterator.next();
      if (VfsUtilCore.isEqualOrAncestor(url.getUrl(), eachFolder.getUrl()) ||
          VfsUtilCore.isEqualOrAncestor(eachFolder.getUrl(), url.getUrl())) {
        iterator.remove();
        Disposer.dispose(eachFolder);
      }
    }

    final JpsModuleSourceRoot jpsModuleSourceRoot =
      JpsElementFactory.getInstance().createModuleSourceRoot(url.getUrl(), rootType, properties);
    addJspSourceFolder(jpsModuleSourceRoot, url.getUrl());

    isJpsSourceFoldersChanged = true;
  }

  public boolean hasRegisteredSourceSubfolder(@NotNull String url) {
    for (JpsSourceFolder eachFolder : myJpsSourceFolders) {
      if (VfsUtilCore.isEqualOrAncestor(url, eachFolder.getUrl())) return true;
    }
    return false;
  }

  @Nullable
  public SourceFolder getSourceFolder(@NotNull String url) {
    for (JpsSourceFolder eachFolder : myJpsSourceFolders) {
      if (eachFolder.getUrl().equals(url)) return eachFolder;
    }
    return null;
  }

  @NotNull
  public String[] getSourceRootUrls(boolean includingTests) {
    List<String> result = new SmartList<>();
    for (JpsSourceFolder eachFolder : myJpsSourceFolders) {
      if (includingTests || !eachFolder.isTestSource()) {
        result.add(eachFolder.getUrl());
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @Nullable
  private ContentEntry getContentRootFor(@NotNull Url url) {
    for (ContentEntry e : myRootModel.getContentEntries()) {
      if (VfsUtilCore.isEqualOrAncestor(e.getUrl(), url.getUrl())) return e;
    }
    return null;
  }

  public void unregisterAll(@NotNull Url url, boolean under) {
    for (Iterator<JpsSourceFolder> iterator = myJpsSourceFolders.iterator(); iterator.hasNext(); ) {
      JpsSourceFolder eachFolder = iterator.next();
      String ancestor = under ? url.getUrl() : eachFolder.getUrl();
      String child = under ? eachFolder.getUrl() : url.getUrl();
      if (VfsUtilCore.isEqualOrAncestor(ancestor, child)) {
        iterator.remove();
        Disposer.dispose(eachFolder);
      }
    }
  }

  private void addJspSourceFolder(@NotNull JpsModuleSourceRoot jpsModuleSourceRoot, @NotNull String url) {
    final JpsContentEntry dummyJpsContentEntry = new JpsContentEntry(myDummyJpsModule, myDummyJpsRootModel, url);
    final JpsSourceFolder jpsSourceFolder = new JpsSourceFolder(jpsModuleSourceRoot, dummyJpsContentEntry);
    myJpsSourceFolders.add(jpsSourceFolder);
    // since dummyJpsContentEntry created for each source folder, we will dispose it on that source folder disposal
    Disposer.register(jpsSourceFolder, dummyJpsContentEntry);
  }

  private static final class ContentFolderComparator implements Comparator<ContentFolder> {
    public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();

    @Override
    public int compare(@NotNull ContentFolder o1, @NotNull ContentFolder o2) {
      return StringUtil.naturalCompare(o1.getUrl(), o2.getUrl());
    }
  }
}
