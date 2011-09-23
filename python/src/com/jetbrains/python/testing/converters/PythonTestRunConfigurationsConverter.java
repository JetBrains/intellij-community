package com.jetbrains.python.testing.converters;

import com.google.common.collect.ImmutableMap;
import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.RunManagerSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import org.jdom.Element;

/**
 * @author yole
 */
public class PythonTestRunConfigurationsConverter extends ConversionProcessor<RunManagerSettings> {
  private static ImmutableMap<String, String> ourTypeToFactoryNameMap = ImmutableMap.<String, String>builder()
    .put("PythonUnitTestConfigurationType", PyBundle.message("runcfg.unittest.display_name"))
    .put("PythonDocTestRunConfigurationType", PyBundle.message("runcfg.doctest.display_name"))
    .put("PythonNoseTestRunConfigurationType", PyBundle.message("runcfg.nosetests.display_name"))
    .put("py.test", PyBundle.message("runcfg.pytest.display_name"))
    .build();
  
  @Override
  public boolean isConversionNeeded(RunManagerSettings runManagerSettings) {
    for (Element e : runManagerSettings.getRunConfigurations()) {
      if (isConversionNeeded(e)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void process(RunManagerSettings runManagerSettings) throws CannotConvertException {
    for (Element element : runManagerSettings.getRunConfigurations()) {
      final String confType = element.getAttributeValue("type");
      final String factoryName = ourTypeToFactoryNameMap.get(confType);
      if (factoryName != null) {
        element.setAttribute("type", PythonTestConfigurationType.ID);
        element.setAttribute("factoryName", factoryName);
      }
    }
  }

  private static boolean isConversionNeeded(Element element) {
    final String confType = element.getAttributeValue("type");
    return ourTypeToFactoryNameMap.containsKey(confType);
  }
}
