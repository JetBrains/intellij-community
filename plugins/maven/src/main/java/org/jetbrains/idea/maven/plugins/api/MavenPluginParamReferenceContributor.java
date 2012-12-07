/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.api;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenPluginParamReferenceContributor extends PsiReferenceContributor {

  private static final Logger LOG = Logger.getInstance(MavenPluginParamReferenceContributor.class);

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(XmlTokenType.XML_DATA_CHARACTERS).withParent(
        XmlPatterns.xmlText().inFile(XmlPatterns.xmlFile().withName("pom.xml"))
      ),
      new MavenPluginParamRefProvider());
  }

  private static class MavenPluginParamRefProvider extends PsiReferenceProvider {

    /**
     * This map contains descriptions of all plugins.
     */
    private volatile Map<String, Map> myMap;

    public Map<String, Map> getMap() {
      Map<String, Map> res = myMap;

      if (res == null) {
        res = new HashMap<String, Map>();

        for (MavenPluginDescriptor pluginDescriptor : MavenPluginDescriptor.EP_NAME.getExtensions()) {
          Pair<String, String> pluginId = parsePluginId(pluginDescriptor.mavenId);

          for (MavenPluginDescriptor.Param param : pluginDescriptor.params) {
            String[] paramPath = param.name.split("/");

            Map pluginsMap = res;

            for (int i = paramPath.length - 1; i >= 0; i--) {
              pluginsMap = getOrCreate(pluginsMap, paramPath[i]);
            }

            ParamInfo paramInfo = new ParamInfo(pluginDescriptor.getPluginDescriptor().getPluginClassLoader(), param.refProvider);

            Map<String, ParamInfo> goalsMap = getOrCreate(pluginsMap, pluginId);

            ParamInfo oldValue = goalsMap.put(param.goal, paramInfo);
            if (oldValue != null) {
              LOG.error("Duplicated maven plugin parameter descriptor: "
                        + pluginId.first + ':' + pluginId.second + " -> "
                        + (param.goal != null ? "[" + param.goal + ']' : "") + param.name);
            }
          }
        }

        myMap = res;
      }

      return res;
    }

    private static Pair<String, String> parsePluginId(String mavenId) {
      int idx = mavenId.indexOf(':');
      if (idx <= 0 || idx == mavenId.length() - 1 || mavenId.lastIndexOf(':') != idx) {
        throw new RuntimeException("Failed to parse mavenId: " + mavenId + " (mavenId should has format 'groupId:artifactId')");
      }

      return new Pair<String, String>(mavenId.substring(0, idx), mavenId.substring(idx + 1));
    }

    @NotNull
    private static <K, V extends Map> V getOrCreate(Map map, K key) {
      Map res = (Map)map.get(key);
      if (res == null) {
        res = new HashMap();
        map.put(key, res);
      }

      return (V)res;
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      XmlText xmlText = (XmlText)element.getParent();

      PsiElement prevSibling = xmlText.getPrevSibling();
      if (!(prevSibling instanceof LeafPsiElement) || ((LeafPsiElement)prevSibling).getElementType() != XmlTokenType.XML_TAG_END) return PsiReference.EMPTY_ARRAY;

      PsiElement nextSibling = xmlText.getNextSibling();
      if (!(nextSibling instanceof LeafPsiElement) || ((LeafPsiElement)nextSibling).getElementType() != XmlTokenType.XML_END_TAG_START) return PsiReference.EMPTY_ARRAY;

      XmlTag paramTag = xmlText.getParentTag();
      if (paramTag == null) return PsiReference.EMPTY_ARRAY;

      XmlTag configurationTag = paramTag;
      DomElement domElement;

      Map m = getMap().get(paramTag.getName());

      while (true) {
        if (m == null) return PsiReference.EMPTY_ARRAY;

        configurationTag = configurationTag.getParentTag();
        if (configurationTag == null) return PsiReference.EMPTY_ARRAY;

        String tagName = configurationTag.getName();
        if ("configuration".equals(tagName)) {
          domElement = DomManager.getDomManager(configurationTag.getProject()).getDomElement(configurationTag);
          if (domElement instanceof MavenDomConfiguration) {
            break;
          }

          if (domElement != null) return PsiReference.EMPTY_ARRAY;
        }

        m = (Map)m.get(tagName);
      }

      Map<Pair<String, String>, Map<String, ParamInfo>> pluginsMap = m;

      MavenDomConfiguration domCfg = (MavenDomConfiguration)domElement;

      MavenDomPlugin domPlugin = domCfg.getParentOfType(MavenDomPlugin.class, true);
      if (domPlugin == null) return PsiReference.EMPTY_ARRAY;

      String pluginGroupId = domPlugin.getGroupId().getStringValue();
      String pluginArtifactId = domPlugin.getArtifactId().getStringValue();

      Map<String, ParamInfo> goalsMap;

      if (pluginGroupId == null) {
        goalsMap = pluginsMap.get(Pair.create("org.apache.maven.plugins", pluginArtifactId));
        if (goalsMap == null) {
          goalsMap = pluginsMap.get(Pair.create("org.codehaus.mojo", pluginArtifactId));
        }
      }
      else {
        goalsMap = pluginsMap.get(Pair.create(pluginGroupId, pluginArtifactId));
      }

      if (goalsMap == null) return PsiReference.EMPTY_ARRAY;

      DomElement parent = domCfg.getParent();
      if (parent instanceof MavenDomPluginExecution) {
        MavenDomGoals goals = ((MavenDomPluginExecution)parent).getGoals();
        for (MavenDomGoal goal : goals.getGoals()) {
          ParamInfo info = goalsMap.get(goal.getStringValue());
          if (info != null) {
            MavenParamReferenceProvider providerInstance = info.getProviderInstance();
            if (providerInstance != null) {
              return providerInstance.getReferencesByElement(element, domCfg, context);
            }
          }
        }
      }

      ParamInfo defaultInfo = goalsMap.get(null);
      if (defaultInfo != null) {
        MavenParamReferenceProvider providerInstance = defaultInfo.getProviderInstance();
        if (providerInstance != null) {
          return providerInstance.getReferencesByElement(element, domCfg, context);
        }
      }

      return PsiReference.EMPTY_ARRAY;
    }
  }

  private static class ParamInfo {
    private final ClassLoader myClassLoader;

    private final String myProviderClass;

    private volatile MavenParamReferenceProvider myProviderInstance;

    public ParamInfo(ClassLoader classLoader, String providerClass) {
      myClassLoader = classLoader;
      myProviderClass = providerClass;
    }

    public MavenParamReferenceProvider getProviderInstance() {
      if (myProviderClass == null) {
        return null;
      }

      MavenParamReferenceProvider res = myProviderInstance;

      if (res == null) {
        Object instance;

        try {
          instance = myClassLoader.loadClass(myProviderClass).newInstance();
        }
        catch (Exception e) {
          throw new RuntimeException("Failed to create reference provider instance", e);
        }

        if (instance instanceof MavenParamReferenceProvider) {
          res = (MavenParamReferenceProvider)instance;
        }
        else {
          final PsiReferenceProvider psiReferenceProvider = (PsiReferenceProvider)instance;

          res = new MavenParamReferenceProvider() {
            @Override
            public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                         @NotNull MavenDomConfiguration domCfg,
                                                         @NotNull ProcessingContext context) {
              return psiReferenceProvider.getReferencesByElement(element, context);
            }
          };
        }

        myProviderInstance = res;
      }

      return res;
    }
  }
}
