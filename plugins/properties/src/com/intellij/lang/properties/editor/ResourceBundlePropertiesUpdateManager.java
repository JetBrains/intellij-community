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
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundlePropertiesUpdateManager {
  private final static Logger LOG = Logger.getInstance(ResourceBundlePropertiesUpdateManager.class);

  private final ResourceBundle myResourceBundle;
  private final CodeStyleManager myCodeStyleManager;
  private boolean myOrdered;
  private boolean myAlphaSorted;
  private List<String> myKeysOrder;

  public ResourceBundlePropertiesUpdateManager(ResourceBundle bundle) {
    myResourceBundle = bundle;
    myCodeStyleManager = CodeStyleManager.getInstance(bundle.getProject());
    reload();
  }

  public void insertNewProperty(String key, String value) {
    if (ApplicationManager.getApplication().isUnitTestMode() && myKeysOrder != null) {
      LOG.assertTrue(!myKeysOrder.contains(key));
    }
    final PropertiesFile propertiesFile = myResourceBundle.getDefaultPropertiesFile();
    if (myAlphaSorted) {
      myCodeStyleManager.reformat(propertiesFile.addProperty(key, value).getPsiElement());
    } else {
      insertPropertyLast(key, value, propertiesFile);
      if (myOrdered) {
        myKeysOrder.add(key);
      }
    }
  }

  public void insertOrUpdateTranslation(String key, String value, final PropertiesFile propertiesFile) throws IncorrectOperationException {
    final IProperty property = propertiesFile.findPropertyByKey(key);
    if (property != null) {
      final String oldValue = property.getValue();
      if (!Comparing.equal(oldValue, value)) {
        property.setValue(value);
        myCodeStyleManager.reformat(property.getPsiElement());
      }
      return;
    }

    if (myOrdered) {
      if (myAlphaSorted) {
        myCodeStyleManager.reformat(propertiesFile.addProperty(key, value).getPsiElement());
        return;
      }
      final Pair<IProperty, Integer> propertyAndPosition = findExistedPrevSiblingProperty(key, propertiesFile);
      myCodeStyleManager.reformat(
        propertiesFile.addPropertyAfter(key, value, propertyAndPosition == null ? null : (Property)propertyAndPosition.getFirst())
          .getPsiElement());
    }
    else {
      insertPropertyLast(key, value, propertiesFile);
    }
  }

  public void deletePropertyIfExist(String key, PropertiesFile file) {
    final IProperty property = file.findPropertyByKey(key);
    if (property != null && myKeysOrder != null) {
      boolean keyExistInOtherPropertiesFiles = false;
      for (PropertiesFile propertiesFile : myResourceBundle.getPropertiesFiles()) {
        if (!propertiesFile.equals(file) && propertiesFile.findPropertyByKey(key) != null) {
          keyExistInOtherPropertiesFiles = true;
          break;
        }
      }
      if (!keyExistInOtherPropertiesFiles) {
        myKeysOrder.remove(key);
      }
    }
    if (property != null) {
      PsiElement anElement = property.getPsiElement();
      if (anElement instanceof PomTargetPsiElement) {
        final PomTarget xmlProperty = ((PomTargetPsiElement)anElement).getTarget();
        LOG.assertTrue(xmlProperty instanceof XmlProperty);
        anElement = ((XmlProperty)xmlProperty).getNavigationElement();
      }
      anElement.delete();
    }
  }

  private Pair<IProperty, Integer> findExistedPrevSiblingProperty(String key, PropertiesFile file) {
    if (myKeysOrder.isEmpty()) {
      return null;
    }
    final int prevPosition = myKeysOrder.indexOf(key);
    for (int i = prevPosition; i >= 0 ; i--) {
      final String prevKey = myKeysOrder.get(i);
      final IProperty property = file.findPropertyByKey(prevKey);
      if (property != null) {
        return Pair.create(property, prevPosition + 1);
      }
    }
    return null;
  }

  private void insertPropertyLast(String key, String value, PropertiesFile propertiesFile) {
    final List<IProperty> properties = propertiesFile.getProperties();
    final IProperty lastProperty = properties.isEmpty() ? null : properties.get(properties.size() - 1);
    myCodeStyleManager.reformat(propertiesFile.addPropertyAfter(key, value, lastProperty).getPsiElement());
  }

  public void reload() {
    final Pair<List<String>, Boolean> keysOrder = keysOrder(myResourceBundle);
    myOrdered = keysOrder != null;
    if (myOrdered) {
      myAlphaSorted = keysOrder.getSecond();
      myKeysOrder = myAlphaSorted ? null : keysOrder.getFirst();
    } else {
      myKeysOrder = null;
    }
  }

  @Nullable
  private static Pair<List<String>, Boolean> keysOrder(final ResourceBundle resourceBundle) {
    final boolean[] isAlphaSorted = new boolean[]{true};
    final GraphGenerator<String> generator = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<String>() {
      @Override
      public Collection<String> getNodes() {
        final Set<String> nodes = new LinkedHashSet<>();
        for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
          for (IProperty property : propertiesFile.getProperties()) {
            final String key = property.getKey();
            if (key != null) {
              nodes.add(key);
            }
          }
        }
        return nodes;
      }

      @Override
      public Iterator<String> getIn(String n) {
        final Collection<String> siblings = new LinkedHashSet<>();
        for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
          for (IProperty property : propertiesFile.findPropertiesByKey(n)) {
            PsiElement sibling = property.getPsiElement().getNextSibling();
            while (sibling instanceof PsiWhiteSpace || sibling instanceof PsiComment) {
              sibling = sibling.getNextSibling();
            }
            if (sibling instanceof IProperty) {
              final String key = ((IProperty)sibling).getKey();
              if (key != null) {
                if (isAlphaSorted[0] && String.CASE_INSENSITIVE_ORDER.compare(n, key) > 0) {
                  isAlphaSorted[0] = false;
                }
                siblings.add(key);
              }
            }
          }
        }
        return siblings.iterator();
      }
    }));
    DFSTBuilder<String> dfstBuilder = new DFSTBuilder<>(generator);
    final boolean acyclic = dfstBuilder.isAcyclic();
    if (acyclic) {
      if (isAlphaSorted[0]) {
        final List<String> sortedNodes = new ArrayList<>(generator.getNodes());
        Collections.sort(sortedNodes, String.CASE_INSENSITIVE_ORDER);
        return Pair.create(sortedNodes, true);
      } else {
        final List<String> dfsNodes = dfstBuilder.getSortedNodes();
        Collections.reverse(dfsNodes);
        return Pair.create(dfsNodes, false);
      }
    }
    else {
      return null;
    }
  }

  @TestOnly
  public boolean isAlphaSorted() {
    return myAlphaSorted;
  }
}
