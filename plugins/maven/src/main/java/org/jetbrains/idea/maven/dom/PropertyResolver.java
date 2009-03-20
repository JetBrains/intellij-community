package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import org.apache.commons.beanutils.BeanAccessLanguageException;
import org.apache.commons.beanutils.BeanUtils;
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

  public static String resolve(Module module,
                               String text,
                               Properties additionalProperties,
                               String propertyEscapeString,
                               String escapedCharacters) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
    MavenProjectModel mavenProject = manager.findProject(module);
    if (mavenProject == null) return text;
    return doResolve(text, mavenProject, additionalProperties, propertyEscapeString, escapedCharacters, new Stack<String>());
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

    return doResolve(text, mavenProject, collectPropertiesFromDOM(mavenProject, dom), null, null, new Stack<String>());
  }

  private static Properties collectPropertiesFromDOM(MavenProjectModel project, DomFileElement<MavenModel> dom) {
    Properties result = new Properties();

    MavenModel mavenModel = dom.getRootElement();
    collectPropertiesFromDOM(result, mavenModel.getProperties());

    for (Profile each : mavenModel.getProfiles().getProfiles()) {
      String id = each.getId().getStringValue();
      if (id == null || !project.getActiveProfilesIds().contains(id)) continue;
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

  private static String doResolve(String text,
                                  MavenProjectModel project,
                                  Properties additionalProperties,
                                  String escapeString,
                                  String escapedCharacters,
                                  Stack<String> resolutionStack) {
    Matcher matcher = PATTERN.matcher(text);

    StringBuffer buff = new StringBuffer();
    StringBuffer dummy = new StringBuffer();
    int last = 0;
    while (matcher.find()) {
      String propText = matcher.group();
      String propName = matcher.group(1);

      int tempLast = last;
      last = matcher.start() + propText.length();

      if (escapeString != null) {
        int pos = matcher.start();
        if (pos > escapeString.length() && text.substring(pos - escapeString.length(), pos).equals(escapeString)) {
          buff.append(text.substring(tempLast, pos - escapeString.length()));
          buff.append(propText);
          matcher.appendReplacement(dummy, "");
          continue;
        }
      }

      String resolved = doResolveProperty(propName, project, additionalProperties);
      if (resolved == null) resolved = propText;
      if (!resolved.equals(propText) && !resolutionStack.contains(propName)) {
        resolutionStack.push(propName);
        resolved = doResolve(resolved, project, additionalProperties, escapeString, escapedCharacters, resolutionStack);
        resolutionStack.pop();
      }
      matcher.appendReplacement(buff, Matcher.quoteReplacement(escapeCharacters(resolved, escapedCharacters)));
    }
    matcher.appendTail(buff);

    return buff.toString();
  }

  private static String escapeCharacters(String text, String escapedCharacters) {
    if (StringUtil.isEmpty(escapedCharacters)) return text;

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (escapedCharacters.indexOf(ch) != -1) {
        builder.append('\\');
      }
      builder.append(ch);
    }
    return builder.toString();
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

    try {
      result = BeanUtils.getNestedProperty(project.getMavenModel(), propName);
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
