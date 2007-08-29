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
package com.intellij.ide.util.projectWizard;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.convert.ModuleConverter;
import com.intellij.ide.impl.convert.ProjectConversionUtil;
import com.intellij.ide.impl.convert.ProjectFileVersion;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.SystemProperties;
import com.intellij.CommonBundle;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 7, 2004
 */
public class ExistingModuleLoader extends ModuleBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.ExistingModuleLoader");

  @NotNull
  public Module createModule(ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    LOG.assertTrue(getName() != null);

    final String moduleFilePath = getModuleFilePath();

    LOG.assertTrue(moduleFilePath != null);
    LOG.assertTrue(new File(moduleFilePath).exists());

    return moduleModel.loadModule(moduleFilePath);
  }

  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    // empty
  }

  public ModuleType getModuleType() {
    return null; // no matter
  }

  public boolean validate(final Project current, final Project dest) {
    if (getName() == null) return false;
    if (getModuleFilePath() == null) return false;
    final File file = new File(getModuleFilePath());
    if (file.exists()) {
      try {
        final Document document = JDOMUtil.loadDocument(file);
        final Element root = document.getRootElement();
        if (!convertModule(current, file, document, root)) {
          return false;
        }
        final Set<String> usedMacros = PathMacrosCollector.getMacroNames(root);
        final Set<String> definedMacros = PathMacros.getInstance().getAllMacroNames();
        usedMacros.remove("$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
        usedMacros.removeAll(definedMacros);
        if (usedMacros.size() > 0) {
          final boolean ok = ProjectManagerImpl.showMacrosConfigurationDialog(current, usedMacros);
          if (!ok) {
            return false;
          }
        }
      }
      catch (JDOMException e) {
        Messages.showMessageDialog(e.getMessage(), IdeBundle.message("title.error.reading.file"), Messages.getErrorIcon());
        return false;
      }
      catch (IOException e) {
        Messages.showMessageDialog(e.getMessage(), IdeBundle.message("title.error.reading.file"), Messages.getErrorIcon());
        return false;
      }
    } else {
      Messages.showErrorDialog(current, IdeBundle.message("title.module.file.does.not.exist"), CommonBundle.message("title.error"));
      return false;
    }
    return true;
  }

  private static boolean convertModule(final Project current, final File file, final Document document, final Element root) throws IOException {
    final ModuleConverter converter = ProjectConversionUtil.getModuleConverter();
    if (converter != null && converter.isConversionNeeded(root)) {
      if (current != null && !ProjectFileVersion.getInstance(current).isConverted()) {
        ProjectFileVersion.getInstance(current).showNotAllowedMessage();
        return false;
      }
      final int res = Messages.showYesNoDialog(current, IdeBundle.message("message.module.file.has.an.older.format.do.you.want.to.convert.it"),
                                               IdeBundle.message("dialog.title.convert.module"), Messages.getQuestionIcon());
      if (res != 0) {
        return false;
      }
      if (!file.canWrite()) {
        Messages.showErrorDialog(current, IdeBundle.message("error.message.cannot.modify.file.0", file.getAbsolutePath()),
                                 IdeBundle.message("dialog.title.convert.module"));
        return false;
      }
      final File backupFile = ProjectConversionUtil.backupFile(file);
      converter.convertModuleRoot(file.getName(), root);
      JDOMUtil.writeDocument(document, file, SystemProperties.getLineSeparator());
      Messages.showInfoMessage(current, IdeBundle.message("message.your.module.was.succesfully.converted.br.old.version.was.saved.to.0", backupFile.getAbsolutePath()),
                               IdeBundle.message("dialog.title.convert.module"));
    }
    return true;
  }
}
