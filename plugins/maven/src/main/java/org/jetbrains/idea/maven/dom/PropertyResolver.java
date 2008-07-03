package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import org.apache.commons.beanutils.BeanAccessLanguageException;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.maven.model.Model;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyResolver {
  private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

  public static String resolve(GenericDomValue<String> value) {
    String text = value.getStringValue();
    if (text == null) return null;

    DomFileElement<MavenModel> dom = value.getRoot();
    VirtualFile virtualFile = dom.getOriginalFile().getVirtualFile();
    return resolve(text, virtualFile, dom);
  }

  public static String resolve(String text, VirtualFile file, DomFileElement<MavenModel> dom) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(dom.getFile().getProject());
    MavenProjectModel mavenProject = manager.findProject(file);
    if (mavenProject == null) return text;

    Properties dynamicProperties = new Properties();
    XmlTag propsTag = dom.getRootElement().getProperties().getXmlTag();
    if (propsTag != null) {
      for (XmlTag each : propsTag.getSubTags()) {
        dynamicProperties.setProperty(each.getName(), each.getValue().getText());
      }
    }
    return resolve(text, mavenProject, dynamicProperties);
  }

  private static String resolve(String text, MavenProjectModel n, Properties additionalProperties) {
    Matcher matcher = PATTERN.matcher(text);

    StringBuffer buff = new StringBuffer();
    while (matcher.find()) {
      String propName = matcher.group(1);
      String resolved = resolveProperty(propName, n, additionalProperties);
      if (resolved == null) resolved = matcher.group();
      matcher.appendReplacement(buff, Matcher.quoteReplacement(resolved));
    }
    matcher.appendTail(buff);

    return buff.toString();
  }

  private static String resolveProperty(String propName, MavenProjectModel n, Properties additionalProperties) {
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

    if (propName.equals("basedir")) return n.getDirectory();
    Model m = n.getMavenProject().getModel();

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

    result = n.getProperties().getProperty(propName);
    if (result != null) return result;

    return null;
  }
}
