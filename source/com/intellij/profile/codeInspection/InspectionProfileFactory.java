/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.profile.DefaultProfileFactory;
import com.intellij.profile.Profile;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * User: anna
 * Date: 29-Nov-2005
 */
public class InspectionProfileFactory extends DefaultProfileFactory implements NamedJDOMExternalizable,
                                                                               ApplicationComponent {
  public InspectionProfileFactory() {
    super(Profile.INSPECTION, new InspectionProfile());
  }

  @NonNls
  public String getComponentName() {
    return "InspectionProfileFactory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  public String getPresentableName() {
    return "Inspection Profiles";
  }

  @NonNls
  public String getExternalFileName() {
    return "inspection.profiles";
  }
}
