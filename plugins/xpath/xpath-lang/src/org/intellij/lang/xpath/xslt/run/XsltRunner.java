/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import org.jetbrains.annotations.NotNull;

/**
 * User: Maxim.Mossienko
 * Date: 26.06.2010
 * Time: 19:59:57
 */
public class XsltRunner extends DefaultProgramRunner {
  @NotNull
  public String getRunnerId() {
    return "XsltProgramRunner";
  }

  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId) && 
           profile instanceof XsltRunConfiguration &&
           !XsltRunSettingsEditor.ALLOW_CHOOSING_SDK; // default java runner will run us when we have Java & can choose sdk
  }
}
