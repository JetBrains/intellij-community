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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenSchemaProvider;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenIcons;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MavenPropertyPsiReference extends MavenPsiReference {
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

    if (myText.equals("basedir") || myText.equals("project.basedir") || myText.equals("pom.basedir")) {
      return resolveBasedir();
    }

    PsiElement result = resolveSystemPropety();
    if (result != null) return result;

    result = processProperties(myProjectDom, new PropertyProcessor<PsiElement>() {
      @Nullable
      public PsiElement process(@NotNull XmlTag property) {
        if (property.getName().equals(myText)) return property;
        return null;
      }
    });
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
  private <T> T processProperties(@NotNull MavenDomProjectModel projectDom, final PropertyProcessor<T> processor) {
    T result;
    MavenProject mavenProjectOrNull = MavenDomUtil.findProject(projectDom);

    result = processSettingsXmlProperties(mavenProjectOrNull, processor);
    if (result != null) return result;

    result = processProjectProperties(projectDom, mavenProjectOrNull, processor);
    if (result != null) return result;

    return new MyMavenParentProjectFileProcessor<T>() {
      protected T doProcessParent(VirtualFile parentFile) {
        MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, parentFile);
        if (parentProjectDom == null) return null;
        MavenProject parentMavenProject = MavenDomUtil.findProject(parentProjectDom);
        return processProjectProperties(parentProjectDom, parentMavenProject, processor);
      }
    }.process(projectDom);
  }

  @Nullable
  private <T> T processSettingsXmlProperties(@Nullable MavenProject mavenProject, PropertyProcessor<T> processor) {
    MavenGeneralSettings settings = myProjectsManager.getGeneralSettings();
    T result;

    for (VirtualFile each : settings.getEffectiveSettingsFiles()) {
      MavenDomSettingsModel settingsDom = MavenDomUtil.getMavenDomModel(myProject, each, MavenDomSettingsModel.class);
      if (settingsDom == null) continue;

      result = processProfilesProperties(settingsDom.getProfiles(), mavenProject, processor);
      if (result != null) return result;
    }
    return null;
  }

  @Nullable
  private <T> T processProjectProperties(MavenDomProjectModel projectDom,
                                         MavenProject mavenProjectOrNull,
                                         PropertyProcessor<T> processor) {
    T result;

    result = processProfilesXmlProperties(MavenDomUtil.getVirtualFile(projectDom), mavenProjectOrNull, processor);
    if (result != null) return result;

    result = processProfilesProperties(projectDom.getProfiles(), mavenProjectOrNull, processor);
    if (result != null) return result;

    return processProperties(projectDom.getProperties(), processor);
  }

  @Nullable
  private <T> T processProfilesXmlProperties(VirtualFile projectFile, MavenProject mavenProjectOrNull, PropertyProcessor<T> processor) {
    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile == null) return null;

    MavenDomProfiles profiles = MavenDomUtil.getMavenDomProfilesModel(myProject, profilesFile);
    if (profiles == null) return null;

    return processProfilesProperties(profiles, mavenProjectOrNull, processor);
  }

  @Nullable
  private <T> T processProfilesProperties(MavenDomProfiles profilesDom, MavenProject mavenProjectOrNull, PropertyProcessor<T> processor) {
    List<String> activePropfiles = mavenProjectOrNull == null ? null : mavenProjectOrNull.getActiveProfilesIds();
    for (MavenDomProfile each : profilesDom.getProfiles()) {
      XmlTag idTag = each.getId().getXmlTag();
      if (idTag == null) continue;
      if (activePropfiles != null && !activePropfiles.contains(idTag.getValue().getText())) continue;

      T result = processProperties(each.getProperties(), processor);
      if (result != null) return result;
    }
    return null;
  }

  @Nullable
  private <T> T processProperties(MavenDomProperties propertiesDom, PropertyProcessor<T> processor) {
    XmlTag propertiesTag = propertiesDom.getXmlTag();
    if (propertiesTag != null) {
      for (XmlTag each : propertiesTag.getSubTags()) {
        T result = processor.process(each);
        if (result != null) return result;
      }
    }
    return null;
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

    result = new MyMavenParentProjectFileProcessor<PsiElement>() {
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
    return result.toArray(new Object[result.size()]);
  }

  protected void collectVariants(List<Object> result) {
    collectBasedirVariants(result);
    collectProjectSchemaVariants(result);
    collectSettingsXmlSchemaVariants(result);
    collectPropertiesVariants(result);
    collectSystemEnvProperties(MavenPropertiesVirtualFileSystem.SYSTEM_PROPERTIES_FILE, null, result);
    collectSystemEnvProperties(MavenPropertiesVirtualFileSystem.ENV_PROPERTIES_FILE, "env.", result);
  }

  private void collectBasedirVariants(List<Object> result) {
    PsiDirectory basedir = getBaseDir();
    result.add(createLookupElement(basedir, "basedir", MavenIcons.MAVEN_ICON));
    result.add(createLookupElement(basedir, "pom.basedir", MavenIcons.MAVEN_ICON));
    result.add(createLookupElement(basedir, "project.basedir", MavenIcons.MAVEN_ICON));
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
    processProperties(myProjectDom, new PropertyProcessor<Object>() {
      public Object process(@NotNull XmlTag property) {
        result.add(createLookupElement(property, property.getName()));
        return null;
      }
    });
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

  private <T> T doProcessSchema(XmlElementDescriptor[] descriptors,
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

  private <T> boolean isCollection(XmlElementDescriptor each) {
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

  private interface PropertyProcessor<T> {
    @Nullable
    T process(@NotNull XmlTag property);
  }

  private interface SchemaProcessor<T> {
    @Nullable
    T process(@NotNull String property, XmlElementDescriptor descriptor);
  }

  private class CollectingSchemaProcessor implements SchemaProcessor {
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

  private abstract class MyMavenParentProjectFileProcessor<T> extends MavenParentProjectFileProcessor<T> {
    protected VirtualFile findManagedFile(@NotNull MavenId id) {
      MavenProject project = myProjectsManager.findProject(id);
      return project == null ? null : project.getFile();
    }

    @Nullable
    public T process(@NotNull MavenDomProjectModel projectDom) {
      MavenDomParent parent = projectDom.getMavenParent();
      MavenParentDesc parentDesc = null;
      if (DomUtil.hasXml(parent)) {
        String parentGroupId = parent.getGroupId().getStringValue();
        String parentArtifactId = parent.getArtifactId().getStringValue();
        String parentVersion = parent.getVersion().getStringValue();
        String parentRelativePath = parent.getRelativePath().getStringValue();
        if (StringUtil.isEmptyOrSpaces(parentRelativePath)) parentRelativePath = "../pom.xml";
        MavenId parentId = new MavenId(parentGroupId, parentArtifactId, parentVersion);
        parentDesc = new MavenParentDesc(parentId, parentRelativePath);
      }

      return process(myProjectsManager.getGeneralSettings(), MavenDomUtil.getVirtualFile(projectDom), parentDesc);
    }
  }
}
