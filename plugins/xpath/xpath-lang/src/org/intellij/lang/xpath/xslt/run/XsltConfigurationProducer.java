// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.lang.xpath.xslt.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class XsltConfigurationProducer extends LazyRunConfigurationProducer<XsltRunConfiguration> {

  @Override
  protected boolean setupConfigurationFromContext(@NotNull XsltRunConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    XmlFile file = getXsltFile(context);
    if (file == null) {
      return false;
    }
    configuration.initFromFile(file);
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull XsltRunConfiguration configuration, @NotNull ConfigurationContext context) {
    XmlFile file = getXsltFile(context);
    return file != null && file.getVirtualFile().getPath().replace('/', File.separatorChar).equals(configuration.getXsltFile());
  }

  private static @Nullable XmlFile getXsltFile(ConfigurationContext context) {
    final XmlFile file = PsiTreeUtil.getParentOfType(context.getPsiLocation(), XmlFile.class, false);
    if (file != null && file.isPhysical() && XsltSupport.isXsltFile(file)) {
      return file;
    }
    return null;
  }

  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return XsltRunConfigType.getInstance().getConfigurationFactories()[0];
  }
}