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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jdom.Element;
import org.tmatesoft.svn.core.internal.io.svn.SVNJSchSession;


public class SvnApplicationSettings implements ApplicationComponent, JDOMExternalizable {
  private SvnFileSystemListener myVFSHandler;

  public static SvnApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(SvnApplicationSettings.class);
  }

  public String getComponentName() {
    return "SvnApplicationSettings";
  }

  public void initComponent() {
    if (myVFSHandler == null) {
      myVFSHandler = new SvnFileSystemListener();
      LocalFileSystem.getInstance().registerAuxiliaryFileOperationsHandler(myVFSHandler);
      CommandProcessor.getInstance().addCommandListener(myVFSHandler);
    }
  }

  public void disposeComponent() {
    SVNJSchSession.shutdown();
    if (myVFSHandler != null) {
      LocalFileSystem.getInstance().unregisterAuxiliaryFileOperationsHandler(myVFSHandler);
      CommandProcessor.getInstance().removeCommandListener(myVFSHandler);
      myVFSHandler = null;
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
