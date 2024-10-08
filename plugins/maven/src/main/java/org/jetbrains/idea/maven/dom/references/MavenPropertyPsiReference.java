// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.DefaultXmlSuppressionProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenSchemaProvider;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.plugins.api.MavenPluginDescriptor;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.vfs.MavenPropertiesVirtualFileSystem;

import javax.swing.*;
import java.util.*;

import static icons.OpenapiIcons.RepositoryLibraryLogo;

public class MavenPropertyPsiReference extends MavenPsiReference implements LocalQuickFixProvider {
  public static final String TIMESTAMP_PROP = "maven.build.timestamp";
  public static final String MULTIPROJECT_DIR_PROP = "maven.multiModuleProjectDirectory";

  @Nullable
  protected final MavenDomProjectModel myProjectDom;
  protected final MavenProject myMavenProject;
  private final boolean mySoft;

  public MavenPropertyPsiReference(MavenProject mavenProject, PsiElement element, String text, TextRange range, boolean isSoft) {
    super(element, text, range);
    myMavenProject = mavenProject;
    mySoft = isSoft;
    myProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    PsiElement result = doResolve();
    if (result == null) {
      if (MavenDomUtil.isMavenFile(getElement())) {
        result = tryResolveToActivationSection();
        if (result == null) return null;
      }
    }

    if (result instanceof XmlTag) {
      XmlTagChild[] children = ((XmlTag)result).getValue().getChildren();
      if (children.length != 1 || !(children[0] instanceof Navigatable)) return result;
      return new MavenPsiElementWrapper(result, (Navigatable)children[0]);
    }

    return result;
  }

  private PsiElement tryResolveToActivationSection() {
    XmlTag xmlTag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
    while (xmlTag != null) {
      if (xmlTag.getName().equals("profile")) {
        XmlTag activation = xmlTag.findFirstSubTag("activation");
        if (activation != null) {
          for (XmlTag propertyTag : activation.findSubTags("property")) {
            XmlTag nameTag = propertyTag.findFirstSubTag("name");
            if (nameTag != null) {
              if (nameTag.getValue().getTrimmedText().equals(myText)) {
                return nameTag;
              }
            }
          }
        }
        break;
      }

      xmlTag = xmlTag.getParentTag();
    }

    return null;
  }

  // See org.apache.maven.project.interpolation.AbstractStringBasedModelInterpolator.createValueSources()
  @Nullable
  protected PsiElement doResolve() {
    boolean hasPrefix = false;
    String unprefixed = myText;

    if (myText.startsWith("pom.")) {
      unprefixed = myText.substring("pom.".length());
      hasPrefix = true;
    }
    else if (myText.startsWith("project.")) {
      unprefixed = myText.substring("project.".length());
      hasPrefix = true;
    }

    MavenProject mavenProject = myMavenProject;

    while (unprefixed.startsWith("parent.")) {
      if (unprefixed.equals("parent.groupId") || unprefixed.equals("parent.artifactId") || unprefixed.equals("parent.version")
          || unprefixed.equals("parent.relativePath")) {
        break;
      }

      MavenId parentId = mavenProject.getParentId();
      if (parentId == null) return null;

      mavenProject = myProjectsManager.findProject(parentId);
      if (mavenProject == null) return null;

      unprefixed = unprefixed.substring("parent.".length());
    }

    if (unprefixed.equals("basedir") || (hasPrefix && mavenProject == myMavenProject && unprefixed.equals("baseUri"))) {
      return getBaseDir(mavenProject);
    }


    if (myText.equals(TIMESTAMP_PROP)) {
      return myElement;
    }

    if (myText.equals(MULTIPROJECT_DIR_PROP)) {
      MavenProject rootProject = myProjectsManager.findRootProject(myMavenProject);
      if(rootProject == null) return null;
      return getBaseDir(rootProject);
    }

    if (hasPrefix) {
      MavenDomProjectModel domProjectModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
      if (domProjectModel != null) {
        PsiElement res = resolveModelProperty(domProjectModel, unprefixed, new HashSet<>());
        if (res != null) {
          return res;
        }
      }
    }

    if (mavenProject.getMavenConfig().containsKey(myText)) {
      return resolveConfigFileProperty(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, mavenProject.getMavenConfig().get(myText));
    }

    if (mavenProject.getJvmConfig().containsKey(myText)) {
      return resolveConfigFileProperty(MavenConstants.JVM_CONFIG_RELATIVE_PATH, mavenProject.getJvmConfig().get(myText));
    }

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(myProject).getSettings();
    if (runnerSettings.getMavenProperties().containsKey(myText) || runnerSettings.getVmOptions().contains("-D" + myText + '=')) {
      return myElement;
    }
    if (MavenUtil.getPropertiesFromMavenOpts().containsKey(myText)) {
      return myElement;
    }

    MavenDomProfile profile = DomUtil.findDomElement(myElement, MavenDomProfile.class);
    if (profile != null) {
      PsiElement result = MavenDomProjectProcessorUtils.findProperty(profile.getProperties(), myText);
      if (result != null) return result;
    }

    MavenDomConfiguration pluginCfg = DomUtil.findDomElement(myElement, MavenDomConfiguration.class);
    if (pluginCfg != null) {
      boolean notFound = MavenPluginDescriptor.processDescriptors(descriptor -> {
        if (descriptor.properties != null) {
          for (MavenPluginDescriptor.ModelProperty property : descriptor.properties) {
            if (property.insideConfigurationOnly && property.name.equals(myText)) {
              return false;
            }
          }
        }
        return true;
      }, pluginCfg);

      if (!notFound) {
        return myElement;
      }
    }

    if (myProjectDom != null) {
      PsiElement result = MavenDomProjectProcessorUtils.searchProperty(myText, myProjectDom, myProject);
      if (result != null) return result;
    }

    if ("maven.home".equals(myText)) {
      return myElement;
    }

    if ("java.home".equals(myText)) {
      PsiElement element = resolveToCustomSystemProperty("java.home", MavenUtil.getModuleJreHome(myProjectsManager, mavenProject));
      if (element != null) {
        return element;
      }
    }

    if ("java.version".equals(myText)) {
      PsiElement element = resolveToCustomSystemProperty("java.version", MavenUtil.getModuleJavaVersion(myProjectsManager, mavenProject));
      if (element != null) {
        return element;
      }
    }

    MavenPropertiesVirtualFileSystem mavenPropertiesVirtualFileSystem = MavenPropertiesVirtualFileSystem.getInstance();

    IProperty property = mavenPropertiesVirtualFileSystem.findSystemProperty(myProject, myText);
    if (property != null) return property.getPsiElement();

    if (myText.startsWith("env.")) {
      property = mavenPropertiesVirtualFileSystem.findEnvProperty(myProject, myText.substring("env.".length()));
      if (property != null) return property.getPsiElement();
    }

    String textWithEnv = "env." + myText;

    property = mavenPropertiesVirtualFileSystem.findSystemProperty(myProject, textWithEnv);
    if (property != null) return property.getPsiElement();

    property = mavenPropertiesVirtualFileSystem.findEnvProperty(myProject, textWithEnv);
    if (property != null) return property.getPsiElement();

    if (!hasPrefix) {
      MavenDomProjectModel domProjectModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
      if (domProjectModel != null) {
        PsiElement res = resolveModelProperty(domProjectModel, unprefixed, new HashSet<>());
        if (res != null) {
          return res;
        }
      }
    }

    if (mavenProject.getProperties().containsKey(myText)) {
      return myElement;
    }

    if (myText.startsWith("settings.")) {
      return resolveSettingsModelProperty();
    }

    if (couldBeResolvedByBuildHelper(myText)) {
      return myElement;
    }

    return null;
  }

  @Nullable
  private PsiElement resolveToCustomSystemProperty(@NotNull String propertyName, @Nullable String propertyValue) {
    if (propertyValue == null) return null;

    PsiFile propFile = PsiFileFactory.getInstance(myProject).createFileFromText("SystemProperties.properties", PropertiesLanguage.INSTANCE,
                                                                                propertyName + '=' + propertyValue);

    return ((PropertiesFile)propFile).getProperties().get(0).getPsiElement();
  }

  private PsiDirectory getBaseDir(@NotNull MavenProject mavenProject) {
    return PsiManager.getInstance(myProject).findDirectory(mavenProject.getDirectoryFile());
  }

  private PsiElement resolveConfigFileProperty(@SystemIndependent String fileRelativePath, String propertyValue) {
    VirtualFile baseDir = VfsUtil.findFile(MavenUtil.getBaseDir(myMavenProject.getDirectoryFile()), false);
    if (baseDir != null) {
      VirtualFile mavenConfigFile = baseDir.findFileByRelativePath(fileRelativePath);
      if (mavenConfigFile != null) {
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(mavenConfigFile);
        if (psiFile != null && psiFile.getChildren().length > 0) {
          return new MavenPsiElementWrapper(psiFile.getFirstChild(), psiFile) {

            @Override
            public String getName() {
              return propertyValue;
            }
          };
        }
      }
    }

    return myElement;
  }

  @Nullable
  private PsiElement resolveSettingsModelProperty() {
    if (!schemaHasProperty(MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL_1_2, myText)) return null;

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
    if (!recursionGuard.add(projectDom)) return null;

    String pathWithProjectPrefix = "project." + path;

    if (!MavenModelClassesProperties.isPathValid(MavenModelClassesProperties.MAVEN_PROJECT_CLASS, path)
      && !MavenModelClassesProperties.isPathValid(MavenModelClassesProperties.MAVEN_MODEL_CLASS, path)) {
      if (!schemaHasProperty(MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL, pathWithProjectPrefix)) return null;
    }

    PsiElement result = MavenDomUtil.findTag(projectDom, pathWithProjectPrefix);
    if (result != null) return result;

    if (pathWithProjectPrefix.equals("project.groupId") || pathWithProjectPrefix.equals("project.version")) {
      return MavenDomUtil.findTag(projectDom, "project.parent." + path);
    }

    result = new MavenDomProjectProcessorUtils.DomParentProjectFileProcessor<PsiElement>(myProjectsManager) {
      @Override
      protected PsiElement doProcessParent(VirtualFile parentFile) {
        MavenDomProjectModel parentProjectDom = MavenDomUtil.getMavenDomProjectModel(myProject, parentFile);
        if (parentProjectDom == null) return null;
        return resolveModelProperty(parentProjectDom, path, recursionGuard);
      }
    }.process(projectDom);
    if (result != null) return result;

    return myElement;
  }

  private boolean schemaHasProperty(String schema, final String property) {
    return processSchema(schema, (eachProperty, descriptor) -> {
      if (eachProperty.equals(property)) return true;
      return null;
    }) != null;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return ElementManipulators.handleContentChange(myElement, myRange, newElementName);
  }

  @Override
  public Object @NotNull [] getVariants() {
    List<Object> result = new ArrayList<>();
    collectVariants(result, new HashSet<>());
    return ArrayUtil.toObjectArray(result);
  }

  protected void collectVariants(final List<Object> result, Set<String> variants) {
    int prefixLength = 0;
    if (myText.startsWith("pom.")) {
      prefixLength = "pom.".length();
    }
    else if (myText.startsWith("project.")) {
      prefixLength = "project.".length();
    }

    MavenProject mavenProject = myMavenProject;
    while (myText.startsWith("parent.", prefixLength)) {
      MavenId parentId = mavenProject.getParentId();
      if (parentId == null) return;

      mavenProject = myProjectsManager.findProject(parentId);
      if (mavenProject == null) return;

      prefixLength += "parent.".length();
    }

    final String prefix = prefixLength == 0 ? null : myText.substring(0, prefixLength);

    PsiDirectory baseDir = getBaseDir(mavenProject);
    addVariant(result, "basedir", baseDir, prefix, RepositoryLibraryLogo);
    if (prefix == null) {
      result.add(createLookupElement(baseDir, "project.baseUri", RepositoryLibraryLogo));
      result.add(createLookupElement(baseDir, "pom.baseUri", RepositoryLibraryLogo));
      result.add(LookupElementBuilder.create(TIMESTAMP_PROP).withIcon(RepositoryLibraryLogo));
      result.add(LookupElementBuilder.create(MULTIPROJECT_DIR_PROP).withIcon(RepositoryLibraryLogo));
    }

    processSchema(MavenSchemaProvider.MAVEN_PROJECT_SCHEMA_URL, (property, descriptor) -> {
      if (property.startsWith("project.")) {
        addVariant(result, property.substring("project.".length()), descriptor, prefix, RepositoryLibraryLogo);
      }
      return null;
    });

    processSchema(MavenSchemaProvider.MAVEN_SETTINGS_SCHEMA_URL_1_2, (property, descriptor) -> {
      result.add(createLookupElement(descriptor, property, RepositoryLibraryLogo));
      return null;
    });

    collectPropertiesVariants(result, variants);
    collectSystemEnvProperties(MavenPropertiesVirtualFileSystem.SYSTEM_PROPERTIES_FILE, null, result, variants);
    collectSystemEnvProperties(MavenPropertiesVirtualFileSystem.ENV_PROPERTIES_FILE, "env.", result, variants);

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(myProject).getSettings();
    for (String prop : runnerSettings.getMavenProperties().keySet()) {
      if (variants.add(prefix)) {
        result.add(LookupElementBuilder.create(prop).withIcon(PlatformIcons.PROPERTY_ICON));
      }
    }
    for (String prop : MavenUtil.getPropertiesFromMavenOpts().keySet()) {
      if (variants.add(prop)) {
        result.add(LookupElementBuilder.create(prop).withIcon(PlatformIcons.PROPERTY_ICON));
      }
    }

    for (String property : myMavenProject.getMavenConfig().keySet()) {
      if (variants.add(property)) {
        result.add(LookupElementBuilder.create(property).withIcon(PlatformIcons.PROPERTY_ICON));
      }
    }
    for (String property : myMavenProject.getJvmConfig().keySet()) {
      if (variants.add(property)) {
        result.add(LookupElementBuilder.create(property).withIcon(PlatformIcons.PROPERTY_ICON));
      }
    }

    for (Object key : myMavenProject.getProperties().keySet()) {
      if (key instanceof String property) {
        if (variants.add(property)) {
          result.add(LookupElementBuilder.create(property).withIcon(PlatformIcons.PROPERTY_ICON));
        }
      }
    }

    MavenDomConfiguration pluginCfg = DomUtil.findDomElement(myElement, MavenDomConfiguration.class);
    if (pluginCfg != null) {
      MavenPluginDescriptor.processDescriptors(descriptor -> {
        if (descriptor.properties != null) {
          for (MavenPluginDescriptor.ModelProperty property : descriptor.properties) {
            if (property.insideConfigurationOnly) {
              result.add(LookupElementBuilder.create(property.name).withIcon(PlatformIcons.PROPERTY_ICON));
            }
          }
        }
        return true;
      }, pluginCfg);
    }
  }

  private static void addVariant(List<Object> result, String name, @NotNull Object element, @Nullable String prefix, @NotNull Icon icon) {
    String nameWithPrefix;
    if (prefix == null) {
      nameWithPrefix = name;
      result.add(createLookupElement(element, "pom." + name, icon));
      result.add(createLookupElement(element, "project." + name, icon));
    }
    else {
      nameWithPrefix = prefix + name;
    }

    result.add(createLookupElement(element, nameWithPrefix, icon));
  }

  private void collectPropertiesVariants(final List<Object> result, Set<String> variants) {
    if (myProjectDom != null) {
      for (XmlTag xmlTag : MavenDomProjectProcessorUtils.collectProperties(myProjectDom, myProject)) {
        String propertyName = xmlTag.getName();
        if (variants.add(propertyName)) {
          result.add(createLookupElement(xmlTag, propertyName, PlatformIcons.PROPERTY_ICON));
        }
      }
    }
  }

  private void collectSystemEnvProperties(String propertiesFileName, @Nullable String prefix, List<Object> result, Set<String> variants) {
    VirtualFile virtualFile = MavenPropertiesVirtualFileSystem.getInstance().findFileByPath(propertiesFileName);
    PropertiesFile file = MavenDomUtil.getPropertiesFile(myProject, virtualFile);
    collectPropertiesFileVariants(file, prefix, result, variants);
  }

  protected static void collectPropertiesFileVariants(@Nullable PropertiesFile file,
                                                      @Nullable String prefix,
                                                      List<Object> result,
                                                      Set<? super String> variants) {
    if (file == null) return;

    for (IProperty each : file.getProperties()) {
      String name = each.getKey();
      if (name != null) {
        if (prefix != null) name = prefix + name;

        if (variants.add(name)) {
          result.add(createLookupElement(each, name, PlatformIcons.PROPERTY_ICON));
        }
      }
    }
  }

  private static LookupElement createLookupElement(@NotNull Object element, @NotNull String name, @Nullable Icon icon) {
    return LookupElementBuilder.create(element, name)
      .withIcon(icon)
      .withPresentableText(name);
  }

  @Nullable
  private <T> T processSchema(String schema, SchemaProcessor<T> processor) {
    VirtualFile file = MavenSchemaProvider.getSchemaFile(schema);
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (!(psiFile instanceof XmlFile xmlFile)) return null;

    XmlDocument document = xmlFile.getDocument();
    XmlNSDescriptor desc = (XmlNSDescriptor)document.getMetaData();
    XmlElementDescriptor[] descriptors = desc.getRootElementsDescriptors(document);
    return doProcessSchema(descriptors, null, processor, new HashSet<>());
  }

  private static <T> T doProcessSchema(XmlElementDescriptor[] descriptors,
                                       String prefix,
                                       SchemaProcessor<T> processor,
                                       Set<XmlElementDescriptor> recursionGuard) {
    for (XmlElementDescriptor each : descriptors) {
      if (isCollection(each)) continue;
      if (!recursionGuard.add(each)) continue;

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

  private static boolean isCollection(XmlElementDescriptor each) {
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

  @FunctionalInterface
  private interface SchemaProcessor<T> {
    @Nullable
    T process(@NotNull String property, XmlElementDescriptor descriptor);
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    return new LocalQuickFix[]{ new MyLocalQuickFix() };
  }

  private static class MyLocalQuickFix implements LocalQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return MavenDomBundle.message("fix.ignore.unresolved.maven.property");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement psiElement = ObjectUtils.notNull(element.getFirstChild(), element);

      DefaultXmlSuppressionProvider xmlSuppressionProvider = new DefaultXmlSuppressionProvider();
      xmlSuppressionProvider.suppressForTag(psiElement, MavenPropertyPsiReferenceProvider.UNRESOLVED_MAVEN_PROPERTY_QUICKFIX_ID);
    }
  }

  /**
   * Some properties could be resolved by "build-helper-maven-plugin" (e.g., `${parsedVersion.majorVersion}` property)
   * @see <a href="https://www.mojohaus.org/build-helper-maven-plugin/parse-version-mojo.html#propertyPrefix">mojohaus documentation</a>
   */
  private boolean couldBeResolvedByBuildHelper(@NotNull String propertyText) {
    MavenPlugin buildHelperPlugin = myMavenProject.findPlugin("org.codehaus.mojo", "build-helper-maven-plugin");
    if (buildHelperPlugin == null) return false;

    Optional<MavenPlugin.Execution> execution = buildHelperPlugin.getExecutions().stream()
      .filter(it -> it.getGoals().contains("parse-version"))
      .findFirst();
    if (execution.isEmpty()) return false;

    String propertyPrefix = "parsedVersion"; // default value
    Element configuration = execution.get().getConfigurationElement();
    if (configuration != null) {
      Element customPrefix = configuration.getChild("propertyPrefix");
      if (customPrefix != null) {
        propertyPrefix = customPrefix.getText();
      }
    }
    return propertyText.startsWith(propertyPrefix + ".");
  }
}
