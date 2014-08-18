package com.jetbrains.python.edu.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.UserTest;
import com.jetbrains.python.edu.editor.StudyEditor;
import com.jetbrains.python.edu.ui.StudyTestContentPanel;
import icons.StudyIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudyEditInputAction extends DumbAwareAction {

  public static final String TEST_TAB_NAME = "test";
  public static final String USER_TEST_INPUT = "input";
  public static final String USER_TEST_OUTPUT = "output";
  private static final Logger LOG = Logger.getInstance(StudyEditInputAction.class.getName());
  private JBEditorTabs tabbedPane;
  private Map<TabInfo, UserTest> myEditableTabs = new HashMap<TabInfo, UserTest>();

  public void showInput(final Project project) {
    final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      final VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
      assert openedFile != null;
      TaskFile taskFile = studyTaskManager.getTaskFile(openedFile);
      assert taskFile != null;
      final Task currentTask = taskFile.getTask();
      tabbedPane = new JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), project);
      tabbedPane.addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          if (newSelection.getIcon() != null) {
            int tabCount = tabbedPane.getTabCount();
            VirtualFile taskDir = openedFile.getParent();
            VirtualFile testsDir = taskDir.findChild(Task.USER_TESTS);
            assert testsDir != null;
            UserTest userTest = createUserTest(testsDir, currentTask);
            userTest.setEditable(true);
            StudyTestContentPanel testContentPanel = new StudyTestContentPanel(userTest);
            TabInfo testTab = addTestTab(tabbedPane.getTabCount(), testContentPanel, currentTask, true);
            myEditableTabs.put(testTab, userTest);
            tabbedPane.addTabSilently(testTab, tabCount - 1);
            tabbedPane.select(testTab, true);
          }
        }
      });
      List<UserTest> userTests = currentTask.getUserTests();
      int i = 1;
      for (UserTest userTest : userTests) {
        String inputFileText = StudyUtils.getFileText(null, userTest.getInput(), false);
        String outputFileText = StudyUtils.getFileText(null, userTest.getOutput(), false);
        StudyTestContentPanel myContentPanel = new StudyTestContentPanel(userTest);
        myContentPanel.addInputContent(inputFileText);
        myContentPanel.addOutputContent(outputFileText);
        TabInfo testTab = addTestTab(i, myContentPanel, currentTask, userTest.isEditable());
        tabbedPane.addTabSilently(testTab, i - 1);
        if (userTest.isEditable()) {
          myEditableTabs.put(testTab, userTest);
        }
        i++;
      }
      TabInfo plusTab = new TabInfo(new JPanel());
      plusTab.setIcon(StudyIcons.Add);
      tabbedPane.addTabSilently(plusTab, tabbedPane.getTabCount());
      final JBPopup hint =
        JBPopupFactory.getInstance().createComponentPopupBuilder(tabbedPane.getComponent(), tabbedPane.getComponent())
          .setResizable(true)
          .setMovable(true)
          .setRequestFocus(true)
          .createPopup();
      StudyEditor selectedStudyEditor = StudyEditor.getSelectedStudyEditor(project);
      assert selectedStudyEditor != null;
      hint.showInCenterOf(selectedStudyEditor.getComponent());
      hint.addListener(new HintClosedListener(currentTask));
    }
  }


  private static void flushBuffer(@NotNull final StringBuilder buffer, @NotNull final File file) {
    PrintWriter printWriter = null;
    try {
      printWriter = new PrintWriter(new FileOutputStream(file));
      printWriter.print(buffer.toString());
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    finally {
      StudyUtils.closeSilently(printWriter);
    }
    StudyUtils.synchronize();
  }

  private static UserTest createUserTest(@NotNull final VirtualFile testsDir, @NotNull final Task currentTask) {
    UserTest userTest = new UserTest();
    List<UserTest> userTests = currentTask.getUserTests();
    int testNum = userTests.size() + 1;
    String inputName = USER_TEST_INPUT + testNum;
    File inputFile = new File(testsDir.getPath(), inputName);
    String outputName = USER_TEST_OUTPUT + testNum;
    File outputFile = new File(testsDir.getPath(), outputName);
    userTest.setInput(inputFile.getPath());
    userTest.setOutput(outputFile.getPath());
    userTests.add(userTest);
    return userTest;
  }

  private TabInfo addTestTab(int nameIndex, final StudyTestContentPanel contentPanel, @NotNull final Task currentTask, boolean toBeClosable) {
    TabInfo testTab = toBeClosable ? createClosableTab(contentPanel, currentTask) : new TabInfo(contentPanel);
    return testTab.setText(TEST_TAB_NAME + String.valueOf(nameIndex));
  }

  private TabInfo createClosableTab(StudyTestContentPanel contentPanel, Task currentTask) {
    TabInfo closableTab = new TabInfo(contentPanel);
    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(closableTab, currentTask));
    closableTab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    return closableTab;
  }

  public void actionPerformed(AnActionEvent e) {
    showInput(e.getProject());
  }

  private class HintClosedListener extends  JBPopupAdapter {
    private final Task myTask;
    private HintClosedListener(@NotNull final Task task) {
      myTask = task;
    }

    @Override
    public void onClosed(LightweightWindowEvent event) {
      for (final UserTest userTest : myTask.getUserTests()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (userTest.isEditable()) {
              File inputFile = new File(userTest.getInput());
              File outputFile = new File(userTest.getOutput());
              flushBuffer(userTest.getInputBuffer(), inputFile);
              flushBuffer(userTest.getOutputBuffer(), outputFile);
            }
          }
        });
      }
    }
  }

  private class CloseTab extends AnAction implements DumbAware {

    private final TabInfo myTabInfo;
    private final Task myTask;

    public CloseTab(final TabInfo info, @NotNull final Task task) {
      myTabInfo = info;
      myTask = task;
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setIcon(tabbedPane.isEditorTabs() ? AllIcons.Actions.CloseNew : AllIcons.Actions.Close);
      e.getPresentation().setHoveredIcon(tabbedPane.isEditorTabs() ? AllIcons.Actions.CloseNewHovered : AllIcons.Actions.CloseHovered);
      e.getPresentation().setVisible(UISettings.getInstance().SHOW_CLOSE_BUTTON);
      e.getPresentation().setText("Delete test");
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      tabbedPane.removeTab(myTabInfo);
      UserTest userTest = myEditableTabs.get(myTabInfo);
      File testInputFile = new File(userTest.getInput());
      File testOutputFile = new File(userTest.getOutput());
      if (testInputFile.delete() && testOutputFile.delete()) {
        StudyUtils.synchronize();
      } else {
        LOG.error("failed to delete user tests");
      }
      myTask.getUserTests().remove(userTest);
    }
  }
}
