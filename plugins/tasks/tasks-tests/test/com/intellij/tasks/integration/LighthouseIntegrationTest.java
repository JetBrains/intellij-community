// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.integration;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.lighthouse.LighthouseRepository;
import com.intellij.tasks.lighthouse.LighthouseRepositoryType;
import org.jdom.Element;

public class LighthouseIntegrationTest extends TaskManagerTestCase {
  // IDEA-206557
  public void testApiTokenNotStoredInSettings() {
    final LighthouseRepository repository = new LighthouseRepository(new LighthouseRepositoryType());
    // We need to save something different from the defaults apart from transient password/token,
    // otherwise XmlSerializer.serialize(...) returns null.
    repository.setUrl("https://test.lighthouseapp.com");
    final String apiToken = "secret";
    repository.setPassword(apiToken);
    final Element serialized = XmlSerializer.serialize(repository);
    final String settingsContent = JDOMUtil.write(serialized);
    assertFalse(settingsContent.contains(apiToken));
  }
}
