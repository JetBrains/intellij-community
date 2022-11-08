package com.jetbrains.performancePlugin;

import com.jetbrains.performancePlugin.commands.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class BaseCommandProvider implements CommandProvider {
  @Override
  public @NotNull Map<String, CreateCommand> getCommands() {
    return Map.<String, CreateCommand>ofEntries(
      Map.entry(RunConfigurationCommand.PREFIX, RunConfigurationCommand::new),
      Map.entry(TakeScreenshotCommand.PREFIX, TakeScreenshotCommand::new),
      Map.entry(OpenFileCommand.PREFIX, OpenFileCommand::new),
      Map.entry(StartProfileCommand.PREFIX, StartProfileCommand::new),
      Map.entry(StopProfileCommand.PREFIX, StopProfileCommand::new),
      Map.entry(InspectionCommand.PREFIX, InspectionCommand::new),
      Map.entry(InspectionCommandEx.PREFIX, InspectionCommandEx::new),
      Map.entry(ReformatCommand.PREFIX, ReformatCommand::new),
      Map.entry(GoToCommand.PREFIX, GoToCommand::new),
      Map.entry(DoLocalInspection.PREFIX, DoLocalInspection::new),
      Map.entry(CompletionCommand.PREFIX, CompletionCommand::new),
      Map.entry(DelayTypeCommand.PREFIX, DelayTypeCommand::new),
      Map.entry(ReplaceTextCommand.PREFIX, ReplaceTextCommand::new),
      Map.entry(ExitAppCommand.PREFIX, ExitAppCommand::new),
      Map.entry(ExitAppWithTimeoutCommand.PREFIX, ExitAppWithTimeoutCommand::new),
      Map.entry(OpenFileWithTerminateCommand.PREFIX, OpenFileWithTerminateCommand::new),
      Map.entry(WaitForSmartCommand.PREFIX, WaitForSmartCommand::new),
      Map.entry(WaitForVcsLogCommand.PREFIX, WaitForVcsLogCommand::new),
      Map.entry(WaitForAsyncRefreshCommand.PREFIX, WaitForAsyncRefreshCommand::new),
      Map.entry(SingleInspectionCommand.PREFIX, SingleInspectionCommand::new),
      Map.entry(StartPowerSave.PREFIX, StartPowerSave::new),
      Map.entry(StopPowerSave.PREFIX, StopPowerSave::new),
      Map.entry(MemoryDumpCommand.PREFIX, MemoryDumpCommand::new),
      Map.entry(CleanCaches.PREFIX, CleanCaches::new),
      Map.entry(FindUsagesCommand.PREFIX, FindUsagesCommand::new),
      Map.entry(IdeEditorKeyCommand.PREFIX, IdeEditorKeyCommand::new),
      Map.entry(ShowAltEnter.PREFIX, ShowAltEnter::new),
      Map.entry(SelectCommand.PREFIX, SelectCommand::new),
      Map.entry(CompareIndicesKt.PREFIX, CompareIndices::new),
      Map.entry(StoreIndices.PREFIX, StoreIndices::new),
      Map.entry(GitCheckoutCommand.PREFIX, GitCheckoutCommand::new),
      Map.entry(OpenProjectView.PREFIX, OpenProjectView::new),
      Map.entry(MoveDirectoryCommand.PREFIX, MoveDirectoryCommand::new),
      Map.entry(RunClassInPlugin.PREFIX, RunClassInPlugin::new),
      Map.entry(SetupProjectSdkCommand.PREFIX, SetupProjectSdkCommand::new),
      Map.entry(OpenProjectCommand.PREFIX, OpenProjectCommand::new),
      Map.entry(CodeAnalysisCommand.PREFIX, CodeAnalysisCommand::new),
      Map.entry(DumpProjectFiles.PREFIX, DumpProjectFiles::new),
      Map.entry(CompareProjectFiles.PREFIX, CompareProjectFiles::new),
      Map.entry(RecordCounterCollectorBaselinesCommand.PREFIX, RecordCounterCollectorBaselinesCommand::new),
      Map.entry(RecordStateCollectorsCommand.PREFIX, RecordStateCollectorsCommand::new),
      Map.entry(CreateAllServicesAndExtensionsCommand.PREFIX, CreateAllServicesAndExtensionsCommand::new),
      Map.entry(RecoveryActionCommand.PREFIX, RecoveryActionCommand::new),
      Map.entry(CorruptIndexesCommand.PREFIX, CorruptIndexesCommand::new),
      Map.entry(FlushIndexesCommand.PREFIX, FlushIndexesCommand::new),
      Map.entry(SearchEverywhereCommand.PREFIX, SearchEverywhereCommand::new),
      Map.entry(SelectFileInProjectViewCommand.PREFIX, SelectFileInProjectViewCommand::new),
      Map.entry(ExpandProjectMenuCommand.PREFIX, ExpandProjectMenuCommand::new),
      Map.entry(DoHighlighting.PREFIX, DoHighlighting::new),
      Map.entry(ReloadFilesCommand.PREFIX, ReloadFilesCommand::new),
      Map.entry(AddFileCommand.PREFIX, AddFileCommand::new),
      Map.entry(DeleteFileCommand.PREFIX, DeleteFileCommand::new),
      Map.entry(TestTipsAndFeaturesIntegrationCommand.PREFIX, TestTipsAndFeaturesIntegrationCommand::new),
      Map.entry(ExpandMainMenuCommand.PREFIX, ExpandMainMenuCommand::new),
      Map.entry(ExpandEditorMenuCommand.PREFIX, ExpandEditorMenuCommand::new),
      Map.entry(OpenRandomFileCommand.PREFIX, OpenRandomFileCommand::new),
      Map.entry(PressEnterKeyCommand.PREFIX, PressEnterKeyCommand::new),
      Map.entry(WaitForDumbCommand.PREFIX, WaitForDumbCommand::new),
      Map.entry(GoToNextPsiElement.PREFIX, GoToNextPsiElement::new)
    );
  }
}
