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
package org.jetbrains.jps.uiDesigner.model;

import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JpsUiDesignerConfigurationSerializationTest {
  @Test
  public void testLoad() {
    JpsProjectData projectData = JpsProjectData.loadFromTestData("plugins/ui-designer/jps-plugin/tests/testData/uiDesigner", getClass());
    JpsProject project = projectData.getProject();
    JpsUiDesignerConfiguration configuration = JpsUiDesignerExtensionService.getInstance().getUiDesignerConfiguration(project);
    assertNotNull(configuration);
    assertTrue(configuration.isInstrumentClasses());
    assertFalse(configuration.isCopyFormsRuntimeToOutput());
  }
}
