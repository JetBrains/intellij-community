package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.maven.model.impl.MavenModuleResourceConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenResourceRootDescriptor;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTarget;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTargetType;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class MavenResourcesBuilder extends TargetBuilder<MavenResourceRootDescriptor, MavenResourcesTarget> {
  public static final String BUILDER_NAME = "maven-resources";
  private static final int FILTERING_SIZE_LIMIT = 10 * 1024 * 1024 /*10 mb*/;
  private static final String MAVEN_BUILD_TIMESTAMP_PROPERTY = "maven.build.timestamp";
  private static final String MAVEN_BUILD_TIMESTAMP_FORMAT_PROPERTY = "maven.build.timestamp.format";

  public MavenResourcesBuilder() {
    super(Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST));
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public void build(@NotNull MavenResourcesTarget target, @NotNull final DirtyFilesHolder<MavenResourceRootDescriptor, MavenResourcesTarget> holder, @NotNull final BuildOutputConsumer outputConsumer, @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    final MavenModuleResourceConfiguration config = target.getModuleResourcesConfiguration(
      context.getProjectDescriptor().dataManager.getDataPaths());
    if (config == null) {
      return;
    }
    final SourceToOutputMapping srcOutMapping = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
    final Set<String> filteringExcludedExtensions = config.getFiltetingExcludedExtensions();
    final String encoding = context.getProjectDescriptor().getEncodingConfiguration().getPreferredModuleEncoding(target.getModule());
    final Date timestamp = new Date();

    holder.processDirtyFiles(new FileProcessor<MavenResourceRootDescriptor, MavenResourcesTarget>() {
      private Map<String, String> myProperties;

      @Override
      public boolean apply(MavenResourcesTarget target, File file, MavenResourceRootDescriptor rd) throws IOException {
        final String relPath = FileUtil.getRelativePath(rd.getRootFile(), file);
        if (relPath == null) {
          return true;
        }
        final String sourcePath = file.getPath();
        if (rd.isIncluded(relPath)) {
          final File outputDir = MavenResourcesTarget.getOutputDir(target.getModuleOutputDir(), rd.getConfiguration());
          if (outputDir != null) {
            final File outputFile = new File(outputDir, relPath);
            boolean shouldFilter = rd.getConfiguration().isFiltered && !filteringExcludedExtensions.contains(getExtension(file));
            if (shouldFilter && file.length() > FILTERING_SIZE_LIMIT) {
              context.processMessage(new CompilerMessage("MavenResources", BuildMessage.Kind.WARNING, "File is too big to be filtered. Most likely it is a binary file and should be excluded from filtering", sourcePath));
              shouldFilter = false;
            }
            if (shouldFilter) {
              final byte[] bytes = FileUtil.loadFileBytes(file);
              final String text = encoding != null? new String(bytes, encoding) : new String(bytes);
              final boolean isProperties = SystemInfo.isFileSystemCaseSensitive? StringUtil.endsWith(file.getName(), ".properties") : StringUtil.endsWithIgnoreCase( file.getName(), ".properties");
              final String escapedCharacters = isProperties ? "\\" : null;
              final Map<String, String> properties = getProperties();


              PrintWriter printWriter = encoding != null? new PrintWriter(outputFile, encoding) : new PrintWriter(outputFile);
              try {
                //MavenPropertyResolver.doFilterText(eachItem.getModule(), text, eachItem.getProperties(), eachItem.getEscapeString(), escapedCharacters, printWriter);
              }
              finally {
                printWriter.close();
              }
            }
            else {
              FileUtil.copyContent(file, outputFile);
            }
            outputConsumer.registerOutputFile(outputFile.getPath(), Collections.singleton(sourcePath));
          }
        }
        else {
          if (!context.isProjectRebuild()) {
            // check if the file has been copied before
            final Collection<String> outputs = srcOutMapping.getOutputs(sourcePath);
            if (outputs != null) {
              for (String output : outputs) {
                new File(output).delete();
              }
              srcOutMapping.remove(sourcePath);
            }
          }
        }
        return true;
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
  }

  public String getDescription() {
    return "Maven Resource Builder";
  }

  private static String getExtension(File file) {
    final String name = file.getName();
    final int dotindex = name.lastIndexOf(".");
    if (dotindex < 0) {
      return "";
    }
    return name.substring(dotindex + 1);
  }
}
