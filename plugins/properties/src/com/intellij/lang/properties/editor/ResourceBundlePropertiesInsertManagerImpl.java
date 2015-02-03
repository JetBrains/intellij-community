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
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundlePropertiesInsertManagerImpl implements ResourceBundlePropertiesInsertManager {
  private final static Logger LOG = Logger.getInstance(ResourceBundlePropertiesInsertManagerImpl.class);

  private final ResourceBundle myResourceBundle;
  private boolean myOrdered;
  private boolean myAlphaSorted;
  private List<String> myKeysOrder;

  private ResourceBundlePropertiesInsertManagerImpl(ResourceBundle bundle) {
    myResourceBundle = bundle;
    reload();
  }

  public static ResourceBundlePropertiesInsertManager create(ResourceBundle bundle) {
    for (PropertiesFile file : bundle.getPropertiesFiles()) {
      if (!(file instanceof PropertiesFileImpl)) {
        return ResourceBundlePropertiesInsertManager.Stub.INSTANCE;
      }
    }
    return new ResourceBundlePropertiesInsertManagerImpl(bundle);
  }

  @Override
  public void insertNewProperty(String key, String value) {
    if (ApplicationManager.getApplication().isUnitTestMode() && myKeysOrder != null) {
      LOG.assertTrue(!myKeysOrder.contains(key));
    }
    final PropertiesFile propertiesFile = myResourceBundle.getDefaultPropertiesFile();
    if (myAlphaSorted) {
      final Pair<IProperty, Integer> propertyAndPosition = findExistedPrevSiblingProperty(key, propertiesFile);
      if (propertyAndPosition == null) {
        propertiesFile.addPropertyFirst(key, value);
        myKeysOrder.add(0, key);
      } else {
        propertiesFile.addPropertyAfter(key, value, (Property)propertyAndPosition.getFirst());
        final Integer position = propertyAndPosition.getSecond();
        myKeysOrder.add(position, key);
      }
    } else {
      propertiesFile.addPropertyLast(key, value);
      if (myOrdered) {
        myKeysOrder.add(key);
      }
    }
  }

  @Override
  public void insertTranslation(String key, String value, final PropertiesFile propertiesFile) {
    if (myOrdered) {
      final Pair<IProperty, Integer> propertyAndPosition = findExistedPrevSiblingProperty(key, propertiesFile);
      if (propertyAndPosition == null) {
        propertiesFile.addPropertyFirst(key, value);
      } else {
        propertiesFile.addPropertyAfter(key, value, (Property)propertyAndPosition.getFirst());
      }
    } else {
      propertiesFile.addPropertyLast(key, value);
    }
  }

  private Pair<IProperty, Integer> findExistedPrevSiblingProperty(String key, PropertiesFile file) {
    if (myKeysOrder.isEmpty()) {
      return null;
    }
    final int prevPosition;
    if (myAlphaSorted) {
      final int rawInsertionPosition = Collections.binarySearch(myKeysOrder, key);
      prevPosition = rawInsertionPosition < 0 ? - rawInsertionPosition - 2 : rawInsertionPosition - 1;
    } else {
      prevPosition = myKeysOrder.indexOf(key);
    }
    for (int i = prevPosition; i >= 0 ; i--) {
      final String prevKey = myKeysOrder.get(i);
      final IProperty property = file.findPropertyByKey(prevKey);
      if (property != null) {
        return Pair.create(property, prevPosition + 1);
      }
    }
    return null;
  }

  @Override
  public void reload() {
    final List<String> keysOrder = keysOrder(myResourceBundle);
    myOrdered = keysOrder != null;
    if (myOrdered) {
      Collections.reverse(keysOrder);
      myAlphaSorted = isAlphaSorted(keysOrder);
      myKeysOrder = keysOrder;
    } else {
      myKeysOrder = null;
    }
  }

  @Nullable
  private static List<String> keysOrder(final ResourceBundle resourceBundle) {
    final GraphGenerator<String> generator = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<String>() {
      @Override
      public Collection<String> getNodes() {
        final Set<String> nodes = new TreeSet<String>();
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
        final Collection<String> siblings = new HashSet<String>();
        for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
          for (IProperty property : propertiesFile.findPropertiesByKey(n)) {
            PsiElement sibling = property.getPsiElement().getNextSibling();
            while (sibling instanceof PsiWhiteSpace || sibling instanceof PsiComment) {
              sibling = sibling.getNextSibling();
            }
            if (sibling instanceof IProperty) {
              final String key = ((IProperty)sibling).getKey();
              if (key != null) {
                siblings.add(key);
              }
            }
          }
          ;
        }
        return siblings.iterator();
      }
    }));
    DFSTBuilder <String> dfstBuilder = new DFSTBuilder<String>(generator);
    final boolean acyclic = dfstBuilder.isAcyclic();
    return acyclic ? dfstBuilder.getSortedNodes() : null;
  }

  private static boolean isAlphaSorted(final List<String> keys) {
    String prevKey = null;
    for (String key : keys) {
      if (prevKey != null && prevKey.compareTo(key) > 0) {
        return false;
      }
      prevKey = key;
    }
    return true;
  }
}
