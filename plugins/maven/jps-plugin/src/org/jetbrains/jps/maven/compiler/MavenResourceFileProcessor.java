/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.maven.model.impl.MavenModuleResourceConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.jetbrains.jps.maven.model.impl.ResourceRootConfiguration;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.JpsProject;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nik
 */
public class MavenResourceFileProcessor {
  private static final int FILTERING_SIZE_LIMIT = 10 * 1024 * 1024 /*10 mb*/;
  private static final String MAVEN_BUILD_TIMESTAMP_PROPERTY = "maven.build.timestamp";
  private static final String MAVEN_BUILD_TIMESTAMP_FORMAT_PROPERTY = "maven.build.timestamp.format";
  protected final Set<String> myFilteringExcludedExtensions;
  protected final JpsEncodingProjectConfiguration myEncodingConfig;
  protected final MavenProjectConfiguration myProjectConfig;
  protected final MavenModuleResourceConfiguration myModuleConfiguration;
  protected final Date myTimestamp;
  private Map<String, String> myProperties;
  private Pattern myDelimitersPattern;

  public MavenResourceFileProcessor(MavenProjectConfiguration projectConfiguration, JpsProject project,
                                    MavenModuleResourceConfiguration moduleConfiguration) {
    myTimestamp = new Date();
    myProjectConfig = projectConfiguration;
    myEncodingConfig = JpsEncodingConfigurationService.getInstance().getEncodingConfiguration(project);
    myModuleConfiguration = moduleConfiguration;
    myFilteringExcludedExtensions = moduleConfiguration.getFilteringExcludedExtensions();
  }

  public void copyFile(File file, File targetFile, ResourceRootConfiguration rootConfiguration, CompileContext context,
                       FileFilter filteringFilter) throws IOException {
    boolean shouldFilter = rootConfiguration.isFiltered && !myFilteringExcludedExtensions.contains(FileUtilRt.getExtension(file.getName()))
                           && filteringFilter.accept(file);
    if (shouldFilter && file.length() > FILTERING_SIZE_LIMIT) {
      context.processMessage(new CompilerMessage("MavenResources", BuildMessage.Kind.WARNING,
                                                 "File is too big to be filtered. Most likely it is a binary file and should be excluded from filtering",
                                                 file.getPath()));
      shouldFilter = false;
    }
    if (shouldFilter) {
      copyWithFiltering(file, targetFile);
    }
    else {
      FileUtil.copyContent(file, targetFile);
    }
  }

  private void copyWithFiltering(File file, File outputFile) throws IOException {
    final String encoding = myEncodingConfig != null? myEncodingConfig.getEncoding(file) : null;
    PrintWriter writer;
    try {
      writer = encoding != null ? new PrintWriter(outputFile, encoding) : new PrintWriter(outputFile);
    }
    catch (FileNotFoundException e) {
      FileUtil.createIfDoesntExist(outputFile);
      writer = encoding != null ? new PrintWriter(outputFile, encoding) : new PrintWriter(outputFile);
    }
    try {
      final byte[] bytes = FileUtil.loadFileBytes(file);
      final String text = encoding != null? new String(bytes, encoding) : new String(bytes);
      doFilterText(text, getDelimitersPattern(), getProperties(), null, writer);
    }
    finally {
      writer.close();
    }
  }

  private void doFilterText(String text, Pattern delimitersPattern,
                            @NotNull Map<String, String> additionalProperties,
                            @Nullable Map<String, String> resolvedPropertiesParam,
                            final Appendable out) throws IOException {
    Map<String, String> resolvedProperties = resolvedPropertiesParam;

    final Matcher matcher = delimitersPattern.matcher(text);

    boolean hasEscapeString = !StringUtil.isEmpty(myModuleConfiguration.escapeString);

    final int groupCount = matcher.groupCount();
    int firstPropertyGroupIndex = hasEscapeString ? 3 : 0;

    int last = 0;
    while (matcher.find()) {
      out.append(text, last, matcher.start());
      last = matcher.end();

      if (hasEscapeString) {
        if (matcher.group(1) != null) {
          out.append(myModuleConfiguration.escapeString).append(myModuleConfiguration.escapeString); // double escape string
          continue;
        }
        else if (matcher.group(2) != null) {
          out.append(matcher.group(3)); // escaped value
          continue;
        }
      }

      String propertyName = null;

      for (int i = firstPropertyGroupIndex; i < groupCount; i++) {
        propertyName = matcher.group(i + 1);
        if (propertyName != null) {
          break;
        }
      }

      assert propertyName != null;

      if (resolvedProperties == null) {
        resolvedProperties = new HashMap<String, String>();
      }

      String propertyValue = resolvedProperties.get(propertyName);
      if (propertyValue == null) {
        if (resolvedProperties.containsKey(propertyName)) { // if cyclic property dependencies
          out.append(matcher.group());
          continue;
        }

        String resolved = myProjectConfig.resolveProperty(propertyName, myModuleConfiguration, additionalProperties);
        if (resolved == null) {
          out.append(matcher.group());
          continue;
        }

        resolvedProperties.put(propertyName, null);

        StringBuilder sb = new StringBuilder();
        doFilterText(resolved, delimitersPattern, additionalProperties, resolvedProperties, sb);
        propertyValue = sb.toString();

        resolvedProperties.put(propertyName, propertyValue);
      }

      if (myModuleConfiguration.escapeWindowsPaths) {
        MavenEscapeWindowsCharacterUtils.escapeWindowsPath(out, propertyValue);
      }
      else {
        out.append(propertyValue);
      }
    }

    out.append(text, last, text.length());
  }

  private Pattern getDelimitersPattern() {
    Pattern pattern = myDelimitersPattern;
    if (pattern == null) {
      if (StringUtil.isEmpty(myModuleConfiguration.escapeString)) {
        pattern = Pattern.compile(myModuleConfiguration.delimitersPattern);
      }
      else {
        String quotedEscapeString = Pattern.quote(myModuleConfiguration.escapeString);
        pattern = Pattern.compile("(" + quotedEscapeString + quotedEscapeString + ")|(?:(" + quotedEscapeString + ")?(" + myModuleConfiguration.delimitersPattern + "))");
      }
      myDelimitersPattern = pattern;
    }
    return pattern;
  }

  private Map<String, String> getProperties() {
    Map<String, String> props = myProperties;
    if (props == null) {
      props = new HashMap<String, String>(myModuleConfiguration.properties);
      String timestampFormat = props.get(MAVEN_BUILD_TIMESTAMP_FORMAT_PROPERTY);
      if (timestampFormat == null) {
        timestampFormat = "yyyyMMdd-HHmm"; // See ModelInterpolator.DEFAULT_BUILD_TIMESTAMP_FORMAT
      }
      props.put(MAVEN_BUILD_TIMESTAMP_PROPERTY, new SimpleDateFormat(timestampFormat).format(myTimestamp));
      myProperties = props;
    }
    return props;
  }
}
