package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.ExtensionsKt;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.diagnostic.telemetry.TraceUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath;
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class FindUsagesCommand extends AbstractCommand {
  private static final String DUMP_FOUND_USAGES_DESTINATION_FILE = "find.usages.command.found.usages.list.file";
  private static final ObjectMapper objectMapper = ExtensionsKt.jacksonObjectMapper()
    .setDefaultPrettyPrinter(new DefaultPrettyPrinter());

  public static final String PREFIX = CMD_PREFIX + "findUsages";
  private static final Logger LOG = Logger.getInstance(FindUsagesCommand.class);
  public static final String SPAN_NAME = "findUsages";

  public FindUsagesCommand(String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    CountDownLatch findUsagesFinished = new CountDownLatch(1);
    List<Usage> allUsages = new ArrayList<>();
    @NotNull Project project = context.getProject();
    TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, SPAN_NAME, span -> {
      DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(() -> {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
          span.setStatus(StatusCode.ERROR, "The action invoked without editor");
          actionCallback.reject("The action invoked without editor");
          return;
        }
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
          span.setStatus(StatusCode.ERROR, "Psi File is not found");
          actionCallback.reject("Psi File is not found");
          return;
        }
        int offset = editor.getCaretModel().getOffset();
        PsiElement element;
        span.addEvent("Finding declaration");
        if (GotoDeclarationAction.findElementToShowUsagesOf(editor, offset) == null) {
          element = GotoDeclarationAction.findTargetElement(project, editor, offset);
        }
        else {
          element = GotoDeclarationAction.findElementToShowUsagesOf(editor, offset);
        }
        if (element == null) {
          span.setStatus(StatusCode.ERROR, "Can't find an element under " + offset + " offset.");
          actionCallback.reject("Can't find an element under " + offset + " offset.");
          return;
        }
        span.addEvent("Command find usages is called on element " + element);
        LOG.info("Command find usages is called on element " + element);
        FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
        FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(
          element, FindUsagesHandlerFactory.OperationMode.HIGHLIGHT_USAGES
        );
        if (handler == null) {
          span.setStatus(StatusCode.ERROR, "No find usage handler found for the element:" + element.getText());
          actionCallback.reject("No find usage handler found for the element:" + element.getText());
          return;
        }
        FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project);
        findUsagesOptions.isUsages = true;
        findUsagesOptions.isSearchForTextOccurrences = false;
        findUsagesOptions.searchScope = GlobalSearchScope.allScope(project);
        Processor<Usage> collectProcessor = Processors.cancelableCollectProcessor(Collections.synchronizedList(allUsages));
        FindUsagesManager.startProcessUsages(handler,
                                             handler.getPrimaryElements(),
                                             handler.getSecondaryElements(),
                                             collectProcessor,
                                             findUsagesOptions,
                                             () -> {
                                               findUsagesFinished.countDown();
                                             });
      }));
      try {
        findUsagesFinished.await();
        span.setAttribute("number", allUsages.size());
      }
      catch (InterruptedException e) {
        span.recordException(e);
        throw new RuntimeException(e);
      }
    });
    storeMetricsDumpFoundUsages(allUsages, project);
    actionCallback.setDone();
    return Promises.toPromise(actionCallback);
  }

  public static void storeMetricsDumpFoundUsages(List<Usage> allUsages, @NotNull Project project) {
    List<FoundUsage> foundUsages = ContainerUtil.map(allUsages, usage -> convertToFoundUsage(project, usage));
    Path jsonPath = getFoundUsagesJsonPath();
    if (jsonPath != null) {
      dumpFoundUsagesToFile(foundUsages, jsonPath);
    }
  }

  public static void dumpFoundUsagesToFile(@NotNull List<FoundUsage> foundUsages,
                                           @NotNull Path jsonPath) {
    LOG.info("Found usages will be dumped to " + jsonPath);
    Collections.sort(foundUsages);

    FoundUsagesReport foundUsagesReport = new FoundUsagesReport(foundUsages.size(), foundUsages);
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), foundUsagesReport);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to write found usages to " + jsonPath, e);
    }
  }

  @NotNull
  public static FoundUsage convertToFoundUsage(@NotNull Project project, @NotNull Usage usage) {
    PortableFilePath portableFilePath = null;
    Integer line = null;
    if (usage instanceof UsageInfo2UsageAdapter) {
      UsageInfo2UsageAdapter adapter = (UsageInfo2UsageAdapter)usage;
      VirtualFile file = ReadAction.compute(() -> adapter.getFile());
      if (file != null) {
        portableFilePath = PortableFilePaths.INSTANCE.getPortableFilePath(file, project);
      }
      line = adapter.getLine() + 1;
    }
    String text = ReadAction.compute(() -> usage.getPresentation().getPlainText());
    return new FoundUsage(text, portableFilePath, line);
  }

  @Nullable
  public static Path getFoundUsagesJsonPath() {
    String property = System.getProperty(DUMP_FOUND_USAGES_DESTINATION_FILE);
    if (property != null) {
      return Paths.get(property);
    }
    return null;
  }

  @NotNull
  public static FoundUsagesReport parseFoundUsagesReportFromFile(@NotNull Path reportPath) throws IOException {
    return objectMapper.readValue(reportPath.toFile(), FoundUsagesReport.class);
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
    public int compareTo(@NotNull FindUsagesCommand.FoundUsage other) {
      return COMPARATOR.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FoundUsage)) return false;
      FoundUsage usage = (FoundUsage)o;
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
      if (builder.length() > 0) {
        builder.append("\n");
      }
      builder.append(text);
      return builder.toString();
    }
  }
}
