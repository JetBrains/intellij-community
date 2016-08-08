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
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.IProperty;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesAnchorizer {
  private final static Logger LOG = Logger.getInstance(PropertiesAnchorizer.class);

  private final Map<IProperty, PropertyAnchor> myAnchors = new HashMap<>();

  public PropertiesAnchorizer(Project project) {
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        final PsiElement removed = event.getChild();
        if (removed instanceof IProperty) {
          myAnchors.remove(removed);
        }
      }
    });
  }

  public static final class PropertyAnchor {
    private final Collection<IProperty> myProperties;

    public PropertyAnchor(Collection<IProperty> properties) {
      myProperties = new ArrayList<>(properties);
    }

    @NotNull
    public String getName() {
      return getRepresentative().getName();
    }

    @NotNull
    public IProperty getRepresentative() {
      return ContainerUtil.getFirstItem(myProperties);
    }

    @Override
    public String toString() {
      return "PropertyAnchor:" + getName();
    }

    private void addProperties(final Collection<IProperty> properties) {
      myProperties.addAll(properties);
    }
  }

  @NotNull
  public PropertyAnchor get(IProperty property) {
    final PropertyAnchor anchor = myAnchors.get(property);
    LOG.assertTrue(anchor != null);
    return anchor;
  }

  public PropertyAnchor createOrUpdate(final Collection<IProperty> properties) {
    LOG.assertTrue(!properties.isEmpty());

    final List<IProperty> propertiesWithoutAnchor = new SmartList<>();
    PropertyAnchor representativeAnchor = null;
    for (IProperty property : properties) {
      final PropertyAnchor anchor = myAnchors.get(property);
      if (anchor == null) {
        propertiesWithoutAnchor.add(property);
      } else {
        if (representativeAnchor != null) {
          LOG.assertTrue(representativeAnchor == anchor);
        }
        representativeAnchor = anchor;
      }
    }

    if (representativeAnchor == null) {
      LOG.assertTrue(propertiesWithoutAnchor.size() == properties.size());
      representativeAnchor = new PropertyAnchor(properties);
    } else {
      representativeAnchor.addProperties(propertiesWithoutAnchor);
    }

    for (IProperty property : propertiesWithoutAnchor) {
      myAnchors.put(property, representativeAnchor);
    }

    return representativeAnchor;
  }
}
