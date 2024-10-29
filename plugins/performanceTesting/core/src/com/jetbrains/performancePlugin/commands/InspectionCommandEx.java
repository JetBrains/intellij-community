package com.jetbrains.performancePlugin.commands;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.actions.ExportToXMLAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.jetbrains.performancePlugin.utils.ResultsToFileProcessor;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Predicates.nonNull;

/**
 * Command runs code inspection using custom tool.
 * <p>
 * Syntax: %InspectCodeEx [parameters]
 * Example: %InspectCodeEx -directory app -toolShortName RubyResolve
 */
public class InspectionCommandEx extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "InspectCodeEx";
  private static final Logger LOGGER = Logger.getInstance(InspectionCommandEx.class);

  private final Options myOptions = new Options();

  public InspectionCommandEx(@NotNull String text, int line) {
    super(text, line);
    if (text.startsWith(PREFIX)) {
      Args.parse(myOptions, text.substring(PREFIX.length()).trim().split(" "), false);
    }
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull final PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();

    if (StringUtil.isNotEmpty(myOptions.downloadFileUrl)) {
      if (StringUtil.isEmpty(myOptions.toolShortName)) {
        LOGGER.error("myOptions.toolShortName cannot be null if you want to download file for test");
      }
      else {
        downloadTestRequiredFile(myOptions.toolShortName, myOptions.downloadFileUrl);
      }
    }

    @NotNull Project project = context.getProject();
    InspectionManagerEx inspectionManagerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final InspectionProfileManager profileManager = InspectionProfileManager.getInstance(project);
    InspectionProfileImpl profile = new InspectionProfileImpl("temp profile");
    profile.copyFrom(profileManager.getCurrentProfile());

    if (StringUtil.isNotEmpty(myOptions.toolShortName)) {
      profile.disableAllTools(project);
      profile.enableTool(myOptions.toolShortName, project);
    }

    List<Tools> enabledInspectionTools = profile.getAllEnabledInspectionTools(project);
    if (!ArrayUtil.isEmpty(myOptions.inspectionTrueFields)) {
      setInspectionFields(enabledInspectionTools, myOptions.inspectionTrueFields, true);
    }
    if (!ArrayUtil.isEmpty(myOptions.inspectionFalseFields)) {
      setInspectionFields(enabledInspectionTools, myOptions.inspectionFalseFields, false);
    }

    NamedScope namedScope = NamedScopesHolder.getScope(project, myOptions.scopeName);
    AnalysisScope analysisScope = null;
    if (namedScope != null) {
      analysisScope = new AnalysisScope(GlobalSearchScopesCore.filterScope(project, namedScope), project);
    }
    else {
      if (StringUtil.isNotEmpty(myOptions.directory)) {
        VirtualFile directory = VfsUtil.findRelativeFile(ProjectUtil.guessProjectDir(project), myOptions.directory);
        if (directory != null) {
          PsiDirectory psiDirectory = ReadAction.compute(() -> PsiManager.getInstance(project).findDirectory(directory));
          if (psiDirectory != null) {
            analysisScope = new AnalysisScope(psiDirectory);
          }
        }
      }
    }

    if (analysisScope == null) {
      analysisScope = new AnalysisScope(project);
    }

    @SuppressWarnings("TestOnlyProblems")
    GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(project, inspectionManagerEx.getContentManager()) {

      @Override
      public void addView(@NotNull InspectionResultsView view, @NotNull String title, boolean isOffline) {
        super.addView(view, title, isOffline);
        ToolWindow resultWindow = ProblemsView.getToolWindow(project);
        if (resultWindow != null && myOptions.hideResults) {
          resultWindow.hide();
        }
      }

      @Override
      protected void notifyInspectionsFinished(@NotNull AnalysisScope scope) {
        super.notifyInspectionsFinished(scope);

        context.message(PerformanceTestingBundle.message("command.inspection.finish"), getLine());
        if (enabledInspectionTools.size() == 1 && !myOptions.hideResults) {
          try {
            File tempDirectory = FileUtil.createTempDirectory("inspection", "result");
            final InspectionResultsView view = getView();

            if (view != null) {
              ExportToXMLAction.Util.dumpToXml(view.getCurrentProfile(), view.getTree(), view.getProject(),
                                               view.getGlobalInspectionContext(), tempDirectory.toPath());

              File[] files = tempDirectory.listFiles();
              if (files != null) {
                for (File file : files) {
                  if (file.isHidden()) {
                    continue;
                  }
                  String identifier = buildIdentifier(file.getName(), myOptions.inspectionTrueFields, project);
                  context.message("#########", getLine());
                  context.message(file.getName(), getLine());
                  context.message(
                    ArrayUtil.isEmpty(myOptions.inspectionTrueFields) ? null : StringUtil.join(myOptions.inspectionTrueFields, "-"),
                    getLine());
                  context.message(project.getName(), getLine());
                  context.message("#########", getLine());
                  Path path = file.toPath();
                  context.message(path.toString(), getLine());
                  long warningCount;
                  try (Stream<String> lines = Files.lines(path).filter(line -> line.contains("<problem>"))) {
                    warningCount = lines.count();
                  }
                  if (ApplicationManagerEx.isInIntegrationTest()) {
                    if (warningCount < Integer.MAX_VALUE) {
                      Path perfMetricsPath =
                        Paths.get(PathManager.getLogPath()).resolve("performance-metrics").resolve("inspectionMetrics.json");
                      ResultsToFileProcessor.writeMetricsToJson(perfMetricsPath, "inspection_execution", (int)warningCount, null);
                    }
                    else {
                      LOGGER.error("warningCount is greater than Integer.MAX_VALUE");
                    }
                  }
                  reportStatisticsToTeamCity(identifier, String.valueOf(warningCount));
                  saveArtifact(path, identifier);
                }
              }
              else {
                LOGGER.error("tempDirectory.listFiles() is null");
              }
            }
          }
          catch (IOException ex) {
            LOGGER.error(ex);
          }
        }

        actionCallback.setDone();
      }
    };

    inspectionContext.setExternalProfile(profile);
    inspectionContext.doInspections(analysisScope);
    return Promises.toPromise(actionCallback);
  }

  private static void saveArtifact(@NotNull Path path, @NotNull String identifier) {
    // We are currently at IDEA_HOME/ruby/integrationTests/run/RubyMine/bin
    File dest = new File("../../../report/incorrect-resolves/" + identifier + ".xml");
    File destDir = dest.getParentFile();
    if (!destDir.exists() && !destDir.mkdirs()) {
      LOGGER.error("Haven't managed to create directory: " + destDir.getAbsolutePath());
    }
    try {
      Files.move(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
      LOGGER.info("Incorrect resolve artifact saved to " + dest.getAbsolutePath());
    }
    catch (IOException e) {
      LOGGER.error("Cannot save artifact: " + identifier);
      LOGGER.error(e);
    }
  }

  private static void setInspectionFields(@NotNull List<Tools> enabledInspectionTools,
                                          String @NotNull [] fieldNames,
                                          boolean flag) {
    if (enabledInspectionTools.size() == 1) {
      InspectionProfileEntry inspection = enabledInspectionTools.get(0).getTool().getTool();
      Class<? extends InspectionProfileEntry> inspectionClass = inspection.getClass();
      for (String inspectionFieldName : fieldNames) {
        try {
          Field field = inspectionClass.getField(inspectionFieldName);
          field.setBoolean(inspection, flag);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
          LOGGER.error(e);
        }
      }
    }
    else {
      LOGGER.error("Cannot set true flags for more than one inspection");
    }
  }

  private static void downloadTestRequiredFile(@NotNull String toolShortName, @NotNull String downloadUrl) {
    String tempDirectory = FileUtil.getTempDirectory();
    String filename = toolShortName + ".txt";
    File downloadedFile = Paths.get(tempDirectory, filename).toFile();
    if (downloadedFile.exists()) {
      //noinspection ResultOfMethodCallIgnored
      downloadedFile.delete();
    }
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(downloadUrl, filename);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), "Download correct resolves file");
    try {
      downloader.download(new File(tempDirectory));
    }
    catch (IOException e) {
      LOGGER.error(e);
    }
    if (!downloadedFile.exists()) {
      LOGGER.error("Downloaded file doesn't exist");
    }
  }

  @NotNull
  private static String buildIdentifier(@NotNull final String inspectionResultFilename,
                                        final String @Nullable [] inspectionTrueFields,
                                        @NotNull Project project) {

    return Stream.of(project.getName(),
                     StringUtil.trimExtensions(inspectionResultFilename),
                     ArrayUtil.isEmpty(inspectionTrueFields) ? null : StringUtil.join(inspectionTrueFields, "-"),
                     "warning-count")
      .filter(nonNull())
      .collect(Collectors.joining("-"));
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void reportStatisticsToTeamCity(@NotNull String key, @NotNull String value) {
    // As TeamCity doesn't show statistic I report to stdout I will report it twice in order to be able to see it in logs
    System.out.println("Report statistics key = " + key + " value = " + value);
    System.out.println("##teamcity[buildStatisticValue key='" + key + "' value='" + value + "']");
  }

  public static class Options {
    @Nullable
    @Argument
    public String scopeName;

    @Nullable
    @Argument
    public String toolShortName;

    @Argument
    public String @Nullable [] inspectionTrueFields;

    @Argument
    public String @Nullable [] inspectionFalseFields;

    @Nullable
    @Argument
    public String downloadFileUrl;

    @Nullable
    @Argument
    public String directory;

    @Argument
    public boolean hideResults = false;
  }
}
