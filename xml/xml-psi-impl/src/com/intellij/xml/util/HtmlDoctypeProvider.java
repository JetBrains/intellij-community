/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 * @deprecated don't use it
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
@Deprecated
public interface HtmlDoctypeProvider {
  ExtensionPointName<HtmlDoctypeProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.util.htmlDoctypeProvider");


  @Nullable
  XmlDoctype getDoctype(XmlFile file);
}
