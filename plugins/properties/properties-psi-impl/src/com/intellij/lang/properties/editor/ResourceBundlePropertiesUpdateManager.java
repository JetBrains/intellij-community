// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.graph.*;
import gnu.trove.THashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class ResourceBundlePropertiesUpdateManager {
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

  public void insertAfter(@NotNull String key, @NotNull String value, @NotNull String anchor) {
    if (myAlphaSorted || !myOrdered) {
      throw new IllegalStateException("Can't insert new properties by anchor while resource bundle is alpha-sorted");
    }
     final PropertiesFile file = myResourceBundle.getDefaultPropertiesFile();
    final IProperty anchorProperty = file.findPropertyByKey(anchor);
    file.addPropertyAfter(key, value, anchorProperty);
    final int anchorIndex = myKeysOrder.indexOf(anchor);
    myKeysOrder.add(anchorIndex + 1, key);
  }

  public void insertOrUpdateTranslation(String key, String value, final PropertiesFile propertiesFile) throws IncorrectOperationException {
    final IProperty property = propertiesFile.findPropertyByKey(key);
    if (property != null) {
      final String oldValue = property.getValue();
      if (!Objects.equals(oldValue, value)) {
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
        propertiesFile.addPropertyAfter(key, value, propertyAndPosition == null ? null : propertyAndPosition.getFirst())
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
    final List<PropertiesOrder> propertiesOrders =
      ContainerUtil.map(resourceBundle.getPropertiesFiles(), PropertiesOrder::new);

    final boolean[] isAlphaSorted = new boolean[]{true};
    final Graph<String> generator = GraphGenerator.generate(CachingSemiGraph.cache(new InboundSemiGraph<String>() {
      @NotNull
      @Override
      public Collection<String> getNodes() {
        final Set<String> nodes = new LinkedHashSet<>();
        for (PropertiesOrder order : propertiesOrders) {
          nodes.addAll(order.myKeys);
        }
        return nodes;
      }

      @NotNull
      @Override
      public Iterator<String> getIn(String n) {
        final Collection<String> siblings = new LinkedHashSet<>();

        for (PropertiesOrder order : propertiesOrders) {
          for (String nextKey : order.getNext(n)) {
            if (isAlphaSorted[0] && String.CASE_INSENSITIVE_ORDER.compare(n, nextKey) > 0) {
              isAlphaSorted[0] = false;
            }
            siblings.add(nextKey);
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
        sortedNodes.sort(String.CASE_INSENSITIVE_ORDER);
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

  public boolean isAlphaSorted() {
    return myAlphaSorted;
  }

  public boolean isSorted() {
    return myOrdered;
  }

  private static class PropertiesOrder {
    List<String> myKeys;
    Map<String, IntArrayList> myKeyIndices;

    PropertiesOrder(@NotNull PropertiesFile file) {
      final List<IProperty> properties = file.getProperties();
      myKeys = new ArrayList<>(properties.size());
      myKeyIndices = FactoryMap.createMap(k->new IntArrayList(1),()->new THashMap<>(properties.size()));

      int index = 0;
      for (IProperty property : properties) {
        final String key = property.getKey();
        if (key != null) {
          myKeys.add(key);
          myKeyIndices.get(key).add(index);
        }
        index++;
      }
    }

    @NotNull
    public List<String> getNext(@NotNull String key) {
      List<String> nextProperties = null;
      if (myKeyIndices.containsKey(key)) {
        final IntArrayList indices = myKeyIndices.get(key);
        for (int i = 0; i < indices.size(); i++) {
          final int searchIdx = indices.getInt(i) + 1;
          if (searchIdx < myKeys.size()) {
            final String nextProperty = myKeys.get(searchIdx);
            if (nextProperty != null) {
              if (nextProperties == null) {
                nextProperties = new SmartList<>();
              }
              nextProperties.add(nextProperty);
            }
          }
        }
      }
      return nextProperties == null ? Collections.emptyList() : nextProperties;
    }
  }
}
