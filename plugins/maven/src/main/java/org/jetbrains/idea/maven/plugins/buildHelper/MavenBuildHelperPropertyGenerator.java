package org.jetbrains.idea.maven.plugins.buildHelper;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.plugins.api.MavenPropertiesGenerator;

import java.util.List;
import java.util.Properties;

/**
 * @author Sergey Evdokimov
 */
public class MavenBuildHelperPropertyGenerator extends MavenPropertiesGenerator {
  @Override
  public void generate(@NotNull Properties modelProperties,
                       @Nullable String goal,
                       @NotNull MavenPlugin plugin,
                       @Nullable Element cfgElement) {
    if (cfgElement == null) return;

    Element portNames = cfgElement.getChild("portNames");
    if (portNames == null) return;

    List<Element> portName = portNames.getChildren("portName");
    for (Element element : portName) {
      String propertyName = element.getTextTrim();
      if (!propertyName.isEmpty()) {
        modelProperties.setProperty(propertyName, "");
      }
    }
  }
}
