/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
/**
 * @author vlan
 */
abstract class PythonPluginPropertiesBase extends PyCharmPropertiesBase {
  PythonPluginPropertiesBase() {
    super()
  }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) {
    return null
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return null
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return null
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return null
  }
}
