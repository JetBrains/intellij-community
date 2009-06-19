/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;

public class MavenDomElementDescriptorHolder {
  private final Project myProject;
  private volatile XmlNSDescriptorImpl myNSDescriptor;

  public MavenDomElementDescriptorHolder(Project project) {
    myProject = project;
  }

  public static MavenDomElementDescriptorHolder getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MavenDomElementDescriptorHolder.class);
  }

  public XmlElementDescriptor getDescriptor(XmlTag tag) {
    if (!MavenDomUtil.isPomFile(tag.getContainingFile())) return null;

    if (myNSDescriptor == null || !myNSDescriptor.isValid()) {
      synchronized (this) {
        if (myNSDescriptor == null || !myNSDescriptor.isValid()) initDescriptor();
      }
    }
    return myNSDescriptor.getElementDescriptor(tag.getName(), myNSDescriptor.getDefaultNamespace());
  }

  private void initDescriptor() {
    myNSDescriptor = new XmlNSDescriptorImpl();

    String schemaUrl = MavenSchemaProvider.MAVEN_SCHEMA_URL;
    String location = ExternalResourceManager.getInstance().getResourceLocation(schemaUrl);
    if (schemaUrl.equals(location)) return;

    VirtualFile schema;
    try {
      schema = VfsUtil.findFileByURL(new URL(location));
    }
    catch (MalformedURLException ignore) {
      return;
    }

    if (schema != null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(schema);
      if (psiFile instanceof XmlFile) myNSDescriptor.init(psiFile);
    }
  }
}
