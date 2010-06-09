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
package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenSchemaProvider;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenIcons;
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class MavenPropertyPsiReference extends MavenPsiReference {
  private static final Set<String> BASEDIR_PROPS =
    new THashSet<String>(Arrays.asList("basedir", "project.basedir", "pom.basedir", "baseUri", "project.baseUri", "pom.baseUri"));

  private static final String TIMESTAMP_PROP = "maven.build.timestamp";

  protected final MavenDomProjectModel myProjectDom;
  protected final MavenProject myMavenProject;
  private final boolean mySoft;

  public MavenPropertyPsiReference(MavenProject mavenProject, PsiElement element, String text, TextRange range, boolean isSoft) {
    super(element, text, range);
    myMavenProject = mavenProject;
    mySoft = isSoft;
    myProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
  }

  @Nullable
  public PsiElement resolve() {
    PsiElement result = doResolve();
    if (result == null) return result;

    if (result instanceof XmlTag) {
      XmlTagChild[] children = ((XmlTag)result).getValue().getChildren();
      if (children.length != 1 || !(children[0] instanceof Navigatable)) return result;
      return new MavenPsiElementWrapper(result, (Navigatable)children[0]);
    }

    return result;
  }

  // precedence
  // 1. user/system
  // 2. settings.xml
  // 3. profiles.xml
  // 4. profiles in pom.xml
  // 5. pom.xml
  // 6. parent profiles.xml
  // 7. profiles in parent pom.xml
  // 8. parent pom.xml
  // 9. model
  @Nullable
  protected PsiElement doResolve() {
    if (myText.startsWith("env.")) {
      return resolveEnvPropety();
    }

    if (BASEDIR_PROPS.contains(myText)) {
      return resolveBasedir();
    }

    if (myText.equals(TIMESTAMP_PROP)) {
      return myElement;
    }

    PsiElement result = resolveSystemPropety();
    if (result != null) return result;

    result = MavenDomProjectProcessorUtils.searchProperty(myText, myProjectDom, myProject);
    if (result != null) return result;

    if (myText.startsWith("settings.")) {
      return resolveSettingsModelProperty();
    }

    String modelProperty = myText;
    if (!modelProperty.startsWith("project.")) {
      modelProperty = modelProperty.startsWith("pom.")
                      ? "project." + modelProperty.substring("pom.".length())
                      : "project." + modelProperty;
    }
    return resolveModelProperty(myProjectDom, modelProperty, new THashSet<DomElement>());
  }

  @Nullable
  private PsiElement resolveSystemPropety() {
    return MavenDomUtil.findProperty(myProject,
                                     MavenPropertiesVirtualFileSystem.SYSTEM_PROPERTIES_FILE,
                                     myText);
  }

  @Nullable
  private PsiElement resolveEnvPropety() {
    return MavenDomUtil.findProperty(myProject,
                                     MavenPropertiesVirtualFileSystem.ENV_PROPERTIES_FILE,
                                     myText.substring("env.".length()));
  }

  @Nullable
  private PsiElement resolveBasedir() {
    return getBaseDir();
  }

  private PsiDirectory getBaseDir() {
    return PsiManager.getInstance(myProject).findDirectory(myMavenProject.getDirectoryFile());
  }

  @Nullable
  private PsiElement resolveSettingsModelProperty() {
    if (!schemaHasProperty(MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL, myText)) return null;

    for (VirtualFile each : myProjectsManager.getGeneralSettings().getEffectiveSettingsFiles()) {
      MavenDomSettingsModel settingsDom = MavenDomUtil.getMavenDomModel(myProject, each, MavenDomSettingsModel.class);
      if (settingsDom == null) continue;
      PsiElement result = MavenDomUtil.findTag(settingsDom, myText);
      if (result != null) return result;
    }
    return myElement;
  }

  @Nullable
  private PsiElement resolveModelProperty(@NotNull MavenDomProjectModel projectDom,
                                          @NotNull final String path,
                                          @NotNull final Set<DomElement> recursionGuard) {
    if (recursionGuard.contains(projectDom)) return null;
    recursionGuard.add(projectDom);

    if (!schemaHasProperty(MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL, path)) return null;

    PsiElement result = MavenDomUtil.findTag(projectDom, path);
    if (result != null) return result;

    if (path.equals("project.groupId") || path.equals("project.version")) {
      return MavenDomUtil.findTag(projectDom, path.replace("project.", "project.parent."));
    }

    result = new MavenDomProjectProcessorUtils.DomParentProjectFileProcessor<PsiElement>(myProjectsManager) {
      protected PsiElement doProcessParent(VirtualFile parentFile) {
        MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, parentFile);
        return resolveModelProperty(parentProjectDom, path, recursionGuard);
      }
    }.process(projectDom);
    if (result != null) return result;

    return myElement;
  }

  private boolean schemaHasProperty(String schema, final String property) {
    return processSchema(schema, new SchemaProcessor<Boolean>() {
      @Nullable
      public Boolean process(@NotNull String eachProperty, XmlElementDescriptor descriptor) {
        if (eachProperty.equals(property)) return true;
        return null;
      }
    }) != null;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return ElementManipulators.getManipulator(myElement).handleContentChange(myElement, myRange, newElementName);
  }

  @NotNull
  public Object[] getVariants() {
    List<Object> result = new ArrayList<Object>();
    collectVariants(result);
    return ArrayUtil.toObjectArray(result);
  }

  protected void collectVariants(List<Object> result) {
    collectStandardVariants(result);
    collectProjectSchemaVariants(result);
    collectSettingsXmlSchemaVariants(result);
    collectPropertiesVariants(result);
    collectSystemEnvProperties(MavenPropertiesVirtualFileSystem.SYSTEM_PROPERTIES_FILE, null, result);
    collectSystemEnvProperties(MavenPropertiesVirtualFileSystem.ENV_PROPERTIES_FILE, "env.", result);
  }

  private void collectStandardVariants(List<Object> result) {
    PsiDirectory basedir = getBaseDir();
    for (String each : BASEDIR_PROPS) {
      result.add(createLookupElement(basedir, each, MavenIcons.MAVEN_ICON));
    }
    result.add(createLookupElement(myElement, TIMESTAMP_PROP, MavenIcons.MAVEN_ICON));
  }

  private void collectProjectSchemaVariants(final List<Object> result) {
    processSchema(MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL, new CollectingSchemaProcessor(result) {
      @Override
      public Object process(@NotNull String property, XmlElementDescriptor descriptor) {
        super.process(property, descriptor);
        String prefix = "project.";
        if (property.length() > prefix.length()) {
          String unqualified = property.substring(prefix.length());
          super.process("pom." + unqualified, descriptor);
          super.process(unqualified, descriptor);
        }
        return null;
      }
    });
  }

  private void collectSettingsXmlSchemaVariants(final List<Object> result) {
    processSchema(MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL, new CollectingSchemaProcessor(result));
  }

  private void collectPropertiesVariants(final List<Object> result) {
    Set<XmlTag> properties = MavenDomProjectProcessorUtils.collectProperties(myProjectDom, myProject);
    result.addAll(ContainerUtil.map(properties, new Function<XmlTag, LookupElement>() {
      public LookupElement fun(XmlTag xmlTag) {
        return createLookupElement(xmlTag, xmlTag.getName());
      }
    }));
  }

  private void collectSystemEnvProperties(String propertiesFileName, String prefix, List<Object> result) {
    PropertiesFile file = MavenDomUtil.getPropertiesFile(myProject, propertiesFileName);
    collectPropertiesFileVariants(file, prefix, result);
  }

  protected void collectPropertiesFileVariants(PropertiesFile file, String prefix, List<Object> result) {
    for (Property each : file.getProperties()) {
      String name = each.getKey();
      if (prefix != null) name = prefix + name;
      result.add(createLookupElement(each, name));
    }
  }

  private static LookupElement createLookupElement(Object element, String name) {
    return createLookupElement(element, name, Icons.PROPERTY_ICON);
  }

  private static LookupElement createLookupElement(Object element, String name, Icon icon) {
    return LookupElementBuilder.create(element, name)
      .setIcon(icon)
      .setPresentableText(name);
  }

  @Nullable
  private <T> T processSchema(String schema, SchemaProcessor<T> processor) {
    VirtualFile file = MavenSchemaProvider.getSchemaFile(schema);
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (!(psiFile instanceof XmlFile)) return null;

    XmlFile xmlFile = (XmlFile)psiFile;
    XmlDocument document = xmlFile.getDocument();
    XmlNSDescriptor desc = (XmlNSDescriptor)document.getMetaData();
    XmlElementDescriptor[] descriptors = desc.getRootElementsDescriptors(document);
    return doProcessSchema(descriptors, null, processor, new THashSet<XmlElementDescriptor>());
  }

  private static <T> T doProcessSchema(XmlElementDescriptor[] descriptors,
                                       String prefix,
                                       SchemaProcessor<T> processor,
                                       Set<XmlElementDescriptor> recursionGuard) {
    for (XmlElementDescriptor each : descriptors) {
      if (isCollection(each)) continue;

      if (recursionGuard.contains(each)) continue;
      recursionGuard.add(each);
      try {
        String name = each.getName();
        if (prefix != null) name = prefix + "." + name;

        T result = processor.process(name, each);
        if (result != null) return result;

        result = doProcessSchema(each.getElementsDescriptors(null), name, processor, recursionGuard);
        if (result != null) return result;
      }
      finally {
        recursionGuard.remove(each);
      }
    }

    return null;
  }

  private static <T> boolean isCollection(XmlElementDescriptor each) {
    XmlTag declaration = (XmlTag)each.getDeclaration();
    if (declaration != null) {
      XmlTag complexType = declaration.findFirstSubTag("xs:complexType");
      if (complexType != null) {
        if (complexType.findFirstSubTag("xs:sequence") != null) return true;
      }
    }
    return false;
  }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  private interface SchemaProcessor<T> {
    @Nullable
    T process(@NotNull String property, XmlElementDescriptor descriptor);
  }

  private static class CollectingSchemaProcessor implements SchemaProcessor {
    private final List<Object> myResult;

    public CollectingSchemaProcessor(List<Object> result) {
      myResult = result;
    }

    @Nullable
    public Object process(@NotNull String property, XmlElementDescriptor descriptor) {
      myResult.add(createLookupElement(descriptor, property, MavenIcons.MAVEN_ICON));
      return null;
    }
  }
}
