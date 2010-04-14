package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.converters.PathReferenceConverter;
import com.intellij.util.xml.converters.values.GenericDomValueConvertersRegistry;

import java.io.File;
import java.util.Set;

public class MavenDomConvertersRegistry {
  protected GenericDomValueConvertersRegistry myConvertersRegistry;

  private final Set<String> mySoftConverterTypes = new HashSet<String>();

  public static MavenDomConvertersRegistry getInstance() {
    return ServiceManager.getService(MavenDomConvertersRegistry.class);
  }

  public MavenDomConvertersRegistry() {
    myConvertersRegistry = new GenericDomValueConvertersRegistry();

    initConverters();
    initSoftConverterTypes();
  }

  private void initSoftConverterTypes() {
    mySoftConverterTypes.add(File.class.getCanonicalName());
  }

  private void initConverters() {
    myConvertersRegistry.registerDefaultConverters();

    myConvertersRegistry.registerConverter(PathReferenceConverter.INSTANCE, File.class);
  }

  public GenericDomValueConvertersRegistry getConvertersRegistry() {
    return myConvertersRegistry;
  }

  public boolean isSoft(String type) {
    return mySoftConverterTypes.contains(type);
  }
}
