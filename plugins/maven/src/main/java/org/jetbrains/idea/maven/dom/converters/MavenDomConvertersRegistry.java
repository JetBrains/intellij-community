// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.converters.values.GenericDomValueConvertersRegistry;
import org.jetbrains.idea.maven.dom.references.MavenPathReferenceConverter;

import java.io.File;
import java.util.Set;

public class MavenDomConvertersRegistry {
  protected GenericDomValueConvertersRegistry myConvertersRegistry;

  private final Set<String> mySoftConverterTypes = ContainerUtil.immutableSet(File.class.getCanonicalName());

  public static MavenDomConvertersRegistry getInstance() {
    return ApplicationManager.getApplication().getService(MavenDomConvertersRegistry.class);
  }

  public MavenDomConvertersRegistry() {
    myConvertersRegistry = new GenericDomValueConvertersRegistry();

    initConverters();
  }

  private void initConverters() {
    myConvertersRegistry.registerDefaultConverters();

    myConvertersRegistry.registerConverter(new MavenPathReferenceConverter(), File.class);
  }

  public GenericDomValueConvertersRegistry getConvertersRegistry() {
    return myConvertersRegistry;
  }

  public boolean isSoft(String type) {
    return mySoftConverterTypes.contains(type);
  }
}
