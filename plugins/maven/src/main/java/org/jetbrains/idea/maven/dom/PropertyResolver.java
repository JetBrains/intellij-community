package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import org.apache.commons.beanutils.BeanAccessLanguageException;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.maven.model.Model;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.MavenProperties;
import org.jetbrains.idea.maven.dom.model.Profile;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyResolver {
  private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

  public static String resolve(Module module, String text) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
    MavenProjectModel mavenProject = manager.findProject(module);
    if (mavenProject == null) return text;
    return doResolve(text, mavenProject, new Properties(), new Stack<String>());
  }

  public static String resolve(GenericDomValue<String> value) {
    String text = value.getStringValue();
    if (text == null) return null;

    DomFileElement<MavenModel> dom = value.getRoot();
    return resolve(text, dom);
  }

  public static String resolve(String text, DomFileElement<MavenModel> dom) {
    VirtualFile file = dom.getOriginalFile().getVirtualFile();
    MavenProjectsManager manager = MavenProjectsManager.getInstance(dom.getFile().getProject());
    MavenProjectModel mavenProject = manager.findProject(file);
    if (mavenProject == null) return text;

    return doResolve(text, mavenProject, collectPropertiesFromDOM(mavenProject, dom), new Stack<String>());
  }

  private static Properties collectPropertiesFromDOM(MavenProjectModel project, DomFileElement<MavenModel> dom) {
    Properties result = new Properties();

    MavenModel mavenModel = dom.getRootElement();
    collectPropertiesFromDOM(result, mavenModel.getProperties());

    for (Profile each : mavenModel.getProfiles().getProfiles()) {
      String id = each.getId().getStringValue();
      if (id == null || !project.getActiveProfiles().contains(id)) continue;
      collectPropertiesFromDOM(result, each.getProperties());
    }

    return result;
  }

  private static void collectPropertiesFromDOM(Properties result, MavenProperties props) {
    XmlTag propsTag = props.getXmlTag();
    if (propsTag != null) {
      for (XmlTag each : propsTag.getSubTags()) {
        result.setProperty(each.getName(), each.getValue().getText());
      }
    }
  }

  private static String doResolve(String text, MavenProjectModel project, Properties additionalProperties, Stack<String> resolutionStack) {
    Matcher matcher = PATTERN.matcher(text);

    StringBuffer buff = new StringBuffer();
    while (matcher.find()) {
      String propText = matcher.group();
      String propName = matcher.group(1);
      String resolved = doResolveProperty(propName, project, additionalProperties);
      if (resolved == null) resolved = propText;
      if (!resolved.equals(propText) && !resolutionStack.contains(propName)) {
        resolutionStack.push(propName);
        resolved = doResolve(resolved, project, additionalProperties, resolutionStack);
        resolutionStack.pop();
      }
      matcher.appendReplacement(buff, Matcher.quoteReplacement(resolved));
    }
    matcher.appendTail(buff);

    return buff.toString();
  }

  private static String doResolveProperty(String propName, MavenProjectModel project, Properties additionalProperties) {
    String result;

    result = MavenEmbedderFactory.collectSystemProperties().getProperty(propName);
    if (result != null) return result;

    if (propName.startsWith("project.") || propName.startsWith("pom.")) {
      if (propName.startsWith("pom.")) {
        propName = propName.substring("pom.".length());
      }
      else {
        propName = propName.substring("project.".length());
      }
    }

    if (propName.equals("basedir")) return project.getDirectory();
    Model m = project.getMavenProject().getModel();

    try {
      result = BeanUtils.getNestedProperty(m, propName);
    }
    catch (IllegalAccessException e) {
    }
    catch (BeanAccessLanguageException e) {
    }
    catch (InvocationTargetException e) {
    }
    catch (NoSuchMethodException e) {
    }
    if (result != null) return result;

    result = additionalProperties.getProperty(propName);
    if (result != null) return result;

    result = project.getProperties().getProperty(propName);
    if (result != null) return result;

    return null;
  }
}
