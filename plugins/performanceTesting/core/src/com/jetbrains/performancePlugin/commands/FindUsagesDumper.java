// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath;
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths;
import com.jetbrains.performancePlugin.utils.DataDumper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FindUsagesDumper {
  private static final String DUMP_FOUND_USAGES_DESTINATION_FILE = "find.usages.command.found.usages.list.file";
  private static final Logger LOG = Logger.getInstance(FindUsagesDumper.class);

  public static void storeMetricsDumpFoundUsages(List<Usage> allUsages, @NotNull Project project) {
    List<FoundUsage> foundUsages = ContainerUtil.map(allUsages, usage -> convertToFoundUsage(project, usage));
    Path jsonPath = getFoundUsagesJsonPath();
    if (jsonPath != null) {
      dumpFoundUsagesToFile(foundUsages, jsonPath);
    }
  }

  public static void dumpFoundUsagesToFile(@NotNull @Unmodifiable List<FoundUsage> foundUsages,
                                           @NotNull Path jsonPath) {
    LOG.info("Found usages will be dumped to " + jsonPath);

    FoundUsagesReport foundUsagesReport = new FoundUsagesReport(foundUsages.size(), ContainerUtil.sorted(foundUsages));
    DataDumper.dump(foundUsagesReport, jsonPath);
  }

  public static @NotNull FoundUsage convertToFoundUsage(@NotNull Project project, @NotNull Usage usage) {
    PortableFilePath portableFilePath = null;
    Integer line = null;
    if (usage instanceof UsageInfo2UsageAdapter adapter) {
      VirtualFile file = ReadAction.compute(() -> adapter.getFile());
      if (file != null) {
        portableFilePath = PortableFilePaths.INSTANCE.getPortableFilePath(file, project);
      }
      line = adapter.getLine() + 1;
    }
    String text = ReadAction.compute(() -> usage.getPresentation().getPlainText());
    return new FoundUsage(text, portableFilePath, line);
  }

  public static @Nullable Path getFoundUsagesJsonPath() {
    String property = System.getProperty(DUMP_FOUND_USAGES_DESTINATION_FILE);
    if (property != null) {
      return Paths.get(property);
    }
    return null;
  }

  public static @NotNull FoundUsagesReport parseFoundUsagesReportFromFile(@NotNull Path reportPath) throws IOException {
    return DataDumper.objectMapper.readValue(reportPath.toFile(), FoundUsagesReport.class);
  }

  public static final class FoundUsagesReport {
    public final int totalNumberOfUsages;
    public final List<FoundUsage> usages;

    @JsonCreator
    public FoundUsagesReport(
      @JsonProperty("totalNumberOfUsages") int totalNumberOfUsages,
      @JsonProperty("usages") @NotNull List<FoundUsage> foundUsages
    ) {
      this.totalNumberOfUsages = totalNumberOfUsages;
      this.usages = foundUsages;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static final class FoundUsage implements Comparable<FoundUsage> {
    public final @NotNull String text;
    public final @Nullable PortableFilePath portableFilePath;
    public final @Nullable Integer line;

    @JsonCreator
    private FoundUsage(
      @JsonProperty("text") @NotNull String text,
      @JsonProperty("portableFilePath") @Nullable PortableFilePath portableFilePath,
      @JsonProperty("line") @Nullable Integer line
    ) {
      this.portableFilePath = portableFilePath;
      this.text = text;
      this.line = line;
    }

    private static final Comparator<FoundUsage> COMPARATOR =
      Comparator.<FoundUsage, String>comparing(usage -> usage.portableFilePath != null ? usage.portableFilePath.getPresentablePath() : "")
        .thenComparingInt(usage -> usage.line != null ? usage.line : -1)
        .thenComparing(usage -> usage.text);

    @Override
    public int compareTo(@NotNull FindUsagesDumper.FoundUsage other) {
      return COMPARATOR.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FoundUsage usage)) return false;
      return text.equals(usage.text) &&
             Objects.equals(portableFilePath, usage.portableFilePath) &&
             Objects.equals(line, usage.line);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text, portableFilePath, line);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (portableFilePath != null) {
        builder.append("In file '").append(portableFilePath.getPresentablePath()).append("' ");
      }
      if (line != null) {
        builder.append("(at line #").append(line).append(") ");
      }
      if (!builder.isEmpty()) {
        builder.append("\n");
      }
      builder.append(text);
      return builder.toString();
    }
  }
}
