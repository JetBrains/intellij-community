package org.jetbrains.idea.maven.polyglot.converter;

import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.lang.UrlClassLoader;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultMavenPolyglotConverter implements MavenPolyglotConverter {
  private final Method myReadMethod;
  private final Method myDefaultModelWriteMethod;
  private final Object myModelReader;
  private final Object myDefaultModelWriterClazz;

  private static final String LIB_DIR_NAME = "lib";
  private static final String COMMON_LIB_DIR_NAME = "common";

  public DefaultMavenPolyglotConverter(String libDirName, String modelReaderClass, String modelBuilderClass) {
    Method readMethod = null;
    Method defaultModelWriteMethod = null;
    Object modelReader = null;
    Object defaultModelWriter = null;
    try {
      File base = new File(PathUtil.getJarPathForClass(getClass()), LIB_DIR_NAME);
      List<URL> urls = new ArrayList<URL>();
      addJars(urls, new File(base, COMMON_LIB_DIR_NAME));
      addJars(urls, new File(base, libDirName));

      UrlClassLoader loader = UrlClassLoader.build().urls(urls).get();
      Class<?> modelReaderClazz = loader.loadClass(modelReaderClass);
      Class<?> modelBuilderClazz = loader.loadClass(modelBuilderClass);
      Class<?> executeManagerImplClazz = loader.loadClass("org.sonatype.maven.polyglot.execute.ExecuteManagerImpl");
      Class<?> defaultModelWriterClazz = loader.loadClass("org.apache.maven.model.io.DefaultModelWriter");
      Class<?> modelClazz = loader.loadClass("org.apache.maven.model.Model");
      modelReader = modelReaderClazz.newInstance();
      defaultModelWriter = defaultModelWriterClazz.newInstance();
      Object modelBuilder = modelBuilderClazz.newInstance();
      Object executeManagerImpl = executeManagerImplClazz.newInstance();

      Field builderField = modelReaderClazz.getDeclaredField("builder");
      builderField.setAccessible(true);
      builderField.set(modelReader, modelBuilder);

      Field executeManagerField = modelBuilderClazz.getDeclaredField("executeManager");
      executeManagerField.setAccessible(true);
      executeManagerField.set(modelBuilder, executeManagerImpl);
      executeManagerField = modelReaderClazz.getDeclaredField("executeManager");
      executeManagerField.setAccessible(true);
      executeManagerField.set(modelReader, executeManagerImpl);

      readMethod = modelReaderClazz.getMethod("read", Reader.class, Map.class);

      defaultModelWriteMethod = defaultModelWriterClazz.getMethod("write", Writer.class, Map.class, modelClazz);
    }
    catch (Exception e) {
      // TODO
    }
    myReadMethod = readMethod;
    myModelReader = modelReader;
    myDefaultModelWriteMethod = defaultModelWriteMethod;
    myDefaultModelWriterClazz = defaultModelWriter;
  }

  @Override
  public String convert(String pomFile) {
    Map<String, String> options = new HashMap<String, String>();
    //noinspection unchecked
    options.put("org.apache.maven.model.building.source", "internal");
    try {
      Object internalModel = myReadMethod.invoke(myModelReader, new StringReader(pomFile), options);
      StringWriter xml = new StringWriter();
      myDefaultModelWriteMethod.invoke(myDefaultModelWriterClazz, xml, options, internalModel);
      return xml.toString();
    }
    catch (Exception e) {
      e.printStackTrace();
      // TODO
    }
    return null;
  }

  private static void addJars(List<URL> urls, File file) throws MalformedURLException {
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        urls.add(new URL("file", null, 0, f.getPath()));
      }
    }
  }
}
