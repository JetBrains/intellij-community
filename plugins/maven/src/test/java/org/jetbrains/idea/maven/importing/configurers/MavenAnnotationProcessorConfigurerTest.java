// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.configurers;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.WriteAction;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.junit.Assert;

import static org.jetbrains.idea.maven.importing.configurers.MavenAnnotationProcessorConfigurer.MAVEN_DEFAULT_ANNOTATION_PROFILE;

public class MavenAnnotationProcessorConfigurerTest extends MavenImportingTestCase {

  public void testDisabledAnnotationProcessor() {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>");

    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    ProcessorConfigProfile profile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE);
    Assert.assertNotNull(profile);
    Assert.assertTrue(profile.isEnabled());

    WriteAction.runAndWait(() -> {
      ProcessorConfigProfile p = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE);
      p.setEnabled(false);
    });

    profile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE);
    Assert.assertNotNull(profile);
    Assert.assertFalse(profile.isEnabled());

    importProject();
    profile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE);
    Assert.assertNotNull(profile);
    Assert.assertFalse(profile.isEnabled());
  }
}