/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.context;

import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class RunConfigurationsContextProvider extends WorkingContextProvider {

  private final RunManagerImpl myManager;

  public RunConfigurationsContextProvider(RunManagerImpl manager) {

    myManager = manager;
  }

  @NotNull
  public String getId() {
    return "runConfigurations";
  }

  @NotNull
  public String getDescription() {
    return "Run Configurations";
  }

  public void saveContext(Element toElement) throws WriteExternalException {
    myManager.writeContext(toElement);
    //((RunManagerImpl)myManager).writeExternal(toElement);
  }

  public void loadContext(Element fromElement) throws InvalidDataException {
    myManager.readContext(fromElement);
  }

  public void clearContext() {
//    myManager.
  }
}
