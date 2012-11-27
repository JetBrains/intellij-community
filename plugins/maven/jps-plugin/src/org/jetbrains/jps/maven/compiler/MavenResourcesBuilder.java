package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.BuildOperations;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class MavenResourcesBuilder extends TargetBuilder<MavenResourceRootDescriptor, MavenResourcesTarget> {
  public static final String BUILDER_NAME = "Maven Resources Compiler";
  private static final int FILTERING_SIZE_LIMIT = 10 * 1024 * 1024 /*10 mb*/;
  private static final String MAVEN_BUILD_TIMESTAMP_PROPERTY = "maven.build.timestamp";
  private static final String MAVEN_BUILD_TIMESTAMP_FORMAT_PROPERTY = "maven.build.timestamp.format";

  public MavenResourcesBuilder() {
    super(Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST));
  }

  @Override
  public void build(@NotNull MavenResourcesTarget target, @NotNull final DirtyFilesHolder<MavenResourceRootDescriptor, MavenResourcesTarget> holder, @NotNull final BuildOutputConsumer outputConsumer, @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    final BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    final MavenModuleResourceConfiguration config = target.getModuleResourcesConfiguration(dataPaths);
    if (config == null) {
      return;
    }
    final Set<String> filteringExcludedExtensions = config.getFilteringExcludedExtensions();
    final String encoding = context.getProjectDescriptor().getEncodingConfiguration().getPreferredModuleEncoding(target.getModule());
    final Date timestamp = new Date();

    @Nullable
    final Map<MavenResourcesTarget, Set<File>> cleanedSources;
    if (context.isProjectRebuild()) {
      cleanedSources = null;
    }
    else {
      cleanedSources = BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, holder);
    }

    holder.processDirtyFiles(new FileProcessor<MavenResourceRootDescriptor, MavenResourcesTarget>() {
      private Map<String, String> myProperties;
      private Pattern myDelimitersPattern;

      @Override
      public boolean apply(MavenResourcesTarget target, File file, MavenResourceRootDescriptor rd) throws IOException {
        final String relPath = FileUtil.getRelativePath(rd.getRootFile(), file);
        if (relPath == null) {
          return true;
        }
        final String sourcePath = file.getPath();
        if (!rd.isIncluded(relPath)) {
          return true;
        }
        final File outputDir = MavenResourcesTarget.getOutputDir(target.getModuleOutputDir(), rd.getConfiguration());
        if (outputDir == null) {
          return true;
        }
        final File outputFile = new File(outputDir, relPath);
        boolean shouldFilter = rd.getConfiguration().isFiltered && !filteringExcludedExtensions.contains(getExtension(file));
        if (shouldFilter && file.length() > FILTERING_SIZE_LIMIT) {
          context.processMessage(new CompilerMessage("MavenResources", BuildMessage.Kind.WARNING, "File is too big to be filtered. Most likely it is a binary file and should be excluded from filtering", sourcePath));
          shouldFilter = false;
        }
        try {
          context.processMessage(new ProgressMessage("Copying resources... [" + target.getModule().getName() + "]"));

          if (shouldFilter) {
            copyWithFiltering(file, outputFile);
          }
          else {
            FileUtil.copyContent(file, outputFile);
          }
          outputConsumer.registerOutputFile(outputFile, Collections.singleton(sourcePath));
        }
        catch (UnsupportedEncodingException e) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, "Resource was not copied: " + e.getMessage(), sourcePath));
        }
        finally {
          if (cleanedSources != null) {
            final Set<File> files = cleanedSources.get(target);
            if (files != null) {
              files.remove(file);
            }
          }
        }
        return !context.getCancelStatus().isCanceled();
      }

      private void copyWithFiltering(File file, File outputFile) throws IOException {
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
          doFilterText(
            getDelimitersPattern(), text, projectConfig, config, endsWith(file.getName(), ".properties") ? "\\" : null, getProperties(), null,
            writer
          );
        }
        finally {
          writer.close();
        }
      }

      private Pattern getDelimitersPattern() {
        Pattern pattern = myDelimitersPattern;
        if (pattern == null) {
          pattern = Pattern.compile(config.delimitersPattern);
          myDelimitersPattern = pattern;
        }
        return pattern;
      }

      private Map<String, String> getProperties() {
        Map<String, String> props = myProperties;
        if (props == null) {
          props = new HashMap<String, String>(config.properties);
          String timestampFormat = props.get(MAVEN_BUILD_TIMESTAMP_FORMAT_PROPERTY);
          if (timestampFormat == null) {
            timestampFormat = "yyyyMMdd-HHmm"; // See ModelInterpolator.DEFAULT_BUILD_TIMESTAMP_FORMAT
          }
          props.put(MAVEN_BUILD_TIMESTAMP_PROPERTY, new SimpleDateFormat(timestampFormat).format(timestamp));
          myProperties = props;
        }
        return props;
      }

    });

    context.checkCanceled();

    if (cleanedSources != null) {
      // cleanup mapping for the files that were copied before but not copied now
      for (Map.Entry<MavenResourcesTarget, Set<File>> entry : cleanedSources.entrySet()) {
        final Set<File> files = entry.getValue();
        if (!files.isEmpty()) {
          final SourceToOutputMapping mapping = context.getProjectDescriptor().dataManager.getSourceToOutputMap(entry.getKey());
          for (File file : files) {
            mapping.remove(file.getPath());
          }
        }
      }
    }

    context.processMessage(new ProgressMessage(""));
  }


  @NotNull
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  private static boolean endsWith(final String fileName, final String suffix) {
    return SystemInfo.isFileSystemCaseSensitive? StringUtil.endsWith(fileName, suffix) : StringUtil.endsWithIgnoreCase(fileName, suffix);
  }

  private static String getExtension(File file) {
    final String name = file.getName();
    final int dotindex = name.lastIndexOf(".");
    if (dotindex < 0) {
      return "";
    }
    return name.substring(dotindex + 1);
  }

  private static void doFilterText(Pattern delimitersPattern,
                                   String text,
                                   MavenProjectConfiguration projectConfig,
                                   MavenModuleResourceConfiguration moduleConfig,
                                   final @Nullable String escapedCharacters,
                                   @NotNull Map<String, String> additionalProperties,
                                   @Nullable Map<String, String> resolvedPropertiesParam,
                                   final Appendable out) throws IOException {
    Map<String, String> resolvedProperties = resolvedPropertiesParam;

    final Matcher matcher = delimitersPattern.matcher(text);
    final int groupCount = matcher.groupCount();
    final String escapeString = moduleConfig.escapeString;
    int last = 0;
    while (matcher.find()) {
      if (escapeString != null) {
        int escapeStringStartIndex = matcher.start() - escapeString.length();
        if (escapeStringStartIndex >= last) {
          if (text.startsWith(escapeString, escapeStringStartIndex)) {
            out.append(text, last, escapeStringStartIndex);
            out.append(matcher.group());
            last = matcher.end();
            continue;
          }
        }
      }

      out.append(text, last, matcher.start());
      last = matcher.end();

      String propertyName = null;

      for (int i = 0; i < groupCount; i++) {
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

        String resolved = projectConfig.resolveProperty(propertyName, moduleConfig, additionalProperties);
        if (resolved == null) {
          out.append(matcher.group());
          continue;
        }

        resolvedProperties.put(propertyName, null);

        StringBuilder sb = new StringBuilder();
        doFilterText(delimitersPattern, resolved, projectConfig, moduleConfig, escapedCharacters, additionalProperties, resolvedProperties, sb);
        propertyValue = sb.toString();

        resolvedProperties.put(propertyName, propertyValue);
      }

      if (escapedCharacters == null) {
        out.append(propertyValue);
      }
      else {
        for (int i = 0; i < propertyValue.length(); i++) {
          char ch = propertyValue.charAt(i);
          if (escapedCharacters.indexOf(ch) != -1) {
            out.append('\\');
          }
          out.append(ch);
        }
      }
    }

    out.append(text, last, text.length());
  }

}
