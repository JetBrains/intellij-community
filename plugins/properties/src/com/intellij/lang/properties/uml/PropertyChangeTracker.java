/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.properties.uml;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.PsiChangeTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiFilter;
import com.intellij.uml.UmlChangeTracker;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class PropertyChangeTracker extends UmlChangeTracker<PropertiesFile, Property, PsiElement> {
  private static final PsiFilter<Property> PROPERTY_FILTER =
    new PsiFilter<Property>(Property.class) {
      @Override
      public boolean areEquivalent(Property e1, Property e2) {
        final String key1 = e1.getKey();
        return key1 != null && key1.equals(e2.getKey());
      }
  };
  private HashMap<PropertiesFile, FileStatus> map;

  public PropertyChangeTracker(Project project, @Nullable PsiFile before, @Nullable PsiFile after) {
    super(project, before, after);
  }

  @Override
  public PsiFilter<PropertiesFile>[] getNodeFilters() {
    return new PsiFilter[] {new PsiFilter(PropertiesFile.class)};
  }

  @Override
  public PsiFilter<Property>[] getNodeContentFilters() {
    return new PsiFilter[] {PROPERTY_FILTER};
  }

  @Override
  public Map<PropertiesFile, FileStatus> getNodeElements() {
    if (map == null) {
      map = new HashMap<PropertiesFile, FileStatus>();
      for (PsiFilter<PropertiesFile> filter : getNodeFilters()) {
        map.putAll(PsiChangeTracker.getElementsChanged(getAfter(), getBefore(), filter));
      }
    }
    return map;
  }

  @Override
  public RelationshipInfo[] getRelationships() {
    return RelationshipInfo.EMPTY;
  }

  @Override
  public PsiNamedElement findElementByFQN(Project project, String fqn) {
    final File file = new File(fqn);
    if (file.exists()) {
      final VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (vf != null) {
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
        if (psiFile instanceof PropertiesFile) {
          return psiFile;
        }
      }
    }
    return null;
  }

  @Override
  public String getPresentableName(PsiNamedElement e) {
    return e instanceof Property ? ((Property)e).getKey() : super.getPresentableName(e);
  }
}
