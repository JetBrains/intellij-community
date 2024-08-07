/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.relaxNG;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.NamespaceFilter;
import com.intellij.psi.meta.MetaDataContributor;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.xml.util.HtmlUtil;
import org.intellij.plugins.relaxNG.compact.psi.impl.RncDocument;
import org.intellij.plugins.relaxNG.inspections.RngDomInspection;
import org.intellij.plugins.relaxNG.inspections.UnusedDefineInspection;
import org.intellij.plugins.relaxNG.model.descriptors.RngNsDescriptor;
import org.intellij.plugins.relaxNG.xml.dom.RngDefine;
import org.intellij.plugins.relaxNG.xml.dom.impl.RngDefineMetaData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class RelaxNgMetaDataContributor implements MetaDataContributor {
  public static final String RNG_NAMESPACE = "http://relaxng.org/ns/structure/1.0";

  @Override
  public void contributeMetaData(@NotNull MetaDataRegistrar registrar) {
    registrar.registerMetaData(
      new AndFilter(
        new NamespaceFilter(RNG_NAMESPACE),
        new ClassFilter(XmlDocument.class)
      ),
      RngNsDescriptor.class);

    registrar.registerMetaData(new ClassFilter(RncDocument.class), RngNsDescriptor.class);

    registrar.registerMetaData(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        if (element instanceof XmlTag tag) {
          DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
          return domElement instanceof RngDefine;
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return XmlTag.class.isAssignableFrom(hintClass);
      }
    }, RngDefineMetaData.class);
  }

  public static @NotNull List<Class<? extends LocalInspectionTool>> getInspectionClasses() {
    return Arrays.asList(RngDomInspection.class, UnusedDefineInspection.class);
  }

  static final class ResourceProvider implements StandardResourceProvider {
    @Override
    public void registerResources(ResourceRegistrar registrar) {
      ClassLoader classLoader = getClass().getClassLoader();
      registrar.addStdResource(RNG_NAMESPACE, "resources/relaxng.rng", classLoader);
      registrar.addStdResource(HtmlUtil.SVG_NAMESPACE, "resources/html5-schema/svg20/svg20.rnc", classLoader);
      registrar.addStdResource(HtmlUtil.MATH_ML_NAMESPACE, "resources/html5-schema/mml3/mathml3.rnc", classLoader);
      registrar.addIgnoredResource("http://relaxng.org/ns/compatibility/annotations/1.0");
    }
  }
}
