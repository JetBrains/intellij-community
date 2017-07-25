package training.learn;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.actions.OpenLessonAction;
import training.lang.LangManager;
import training.lang.LangSupport;
import training.learn.exceptons.*;
import training.learn.log.GlobalLessonLog;
import training.ui.LearnToolWindow;
import training.ui.LearnToolWindowFactory;
import training.ui.views.LearnPanel;
import training.ui.views.ModulesPanel;
import training.util.GenModuleXml;

import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author Sergey Karashevich
 */
@State(
        name = "TrainingPluginModules",
        storages = {
                @Storage(
                        file = StoragePathMacros.APP_CONFIG + "/trainingPlugin.xml"
                )
        }
)
public class CourseManager implements PersistentStateComponent<CourseManager.State> {

    private Project learnProject;
    private LearnPanel myLearnPanel;
    public final static String LEARN_PROJECT_NAME = "LearnProject";
    private ModulesPanel modulesPanel;
    public static final String NOTIFICATION_ID = "Training plugin";

    CourseManager() {
        if (myState.modules == null || myState.modules.size() == 0) try {
            initModules();
            learnProject = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<Module, VirtualFile> mapModuleVirtualFile = new HashMap<>();
    private State myState = new State();

    public static CourseManager getInstance() {
        return ServiceManager.getService(CourseManager.class);
    }

    private void initModules() throws JDOMException, IOException, URISyntaxException, BadModuleException, BadLessonException {
        Element modulesRoot = Module.getRootFromPath(GenModuleXml.MODULE_ALLMODULE_FILENAME);
        for (Element element : modulesRoot.getChildren()) {
            if (element.getName().equals(GenModuleXml.MODULE_TYPE_ATTR)) {
                String moduleFilename = element.getAttribute(GenModuleXml.MODULE_NAME_ATTR).getValue();
                final Module module = Module.initModule(moduleFilename);
                addModule(module);
            }
        }
    }


    @Nullable
    public Module getModuleById(String id) {
        final Module[] modules = getModules();
        if (modules == null || modules.length == 0) return null;

        for (Module module : modules) {
            if (module.getId().toUpperCase().equals(id.toUpperCase())) return module;
        }
        return null;
    }

    public void registerVirtualFile(Module module, VirtualFile virtualFile) {
        mapModuleVirtualFile.put(module, virtualFile);
    }

    public boolean isVirtualFileRegistered(VirtualFile virtualFile) {
        return mapModuleVirtualFile.containsValue(virtualFile);
    }

    public void unregisterVirtaulFile(VirtualFile virtualFile) {
        if (!mapModuleVirtualFile.containsValue(virtualFile)) return;
        for (Module module : mapModuleVirtualFile.keySet()) {
            if (mapModuleVirtualFile.get(module).equals(virtualFile)) {
                mapModuleVirtualFile.remove(module);
                return;
            }
        }
    }

    public void unregisterModule(Module module) {
        mapModuleVirtualFile.remove(module);
    }


    public synchronized void openLesson(Project project, final @Nullable Lesson lesson) {

        final AnAction action = ActionManager.getInstance().getAction("learn.open.lesson");

        final Component focusOwner = IdeFocusManager.getInstance(project).getFocusOwner();
        DataContext parent = DataManager.getInstance().getDataContext(focusOwner);
        final DataContext context = SimpleDataContext.getSimpleContext(OpenLessonAction.Companion.getLESSON_DATA_KEY().getName(), lesson, parent);
        final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", context);

        ActionUtil.performActionDumbAware(action, event);

    }

    @Nullable
    public Project getLearnProject() {
        return learnProject;
    }

    public void setLearnProject(Project project) {
        learnProject = project;
    }

    @Nullable
    Project getCurrentProject() {
        final IdeFrame lastFocusedFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
        if (lastFocusedFrame == null) return null;
        return lastFocusedFrame.getProject();
    }


    /**
     * checking environment to start learning plugin. Checking SDK.
     *
     * @param project where lesson should be started
     * @throws OldJdkException     - if project JDK version is not enough for this module
     * @throws InvalidSdkException - if project SDK is not suitable for module
     */
    public void checkEnvironment(@NotNull Project project) throws OldJdkException, InvalidSdkException, NoSdkException, NoJavaModuleException {

        final Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (sdk == null) throw new NoSdkException();
        final SdkTypeId sdkType = sdk.getSdkType();
        //noinspection ConstantConditions
        LangManager.Companion.getInstance().getLangSupport().checkSdkCompatibility(sdk, sdkType);
    }


    @Nullable
    public Lesson findLesson(String lessonName) {
        if (getModules() == null) return null;
        for (Module module : getModules()) {
            for (Lesson lesson : module.getLessons()) {
                if (lesson.getName() != null)
                    if (lesson.getName().toUpperCase().equals(lessonName.toUpperCase()))
                        return lesson;
            }
        }
        return null;
    }

    public void setLearnPanel(LearnPanel learnPanel) {
        myLearnPanel = learnPanel;
    }

    public LearnPanel getLearnPanel() {
        myLearnPanel.updateButtonUi();
        return myLearnPanel;
    }

    public void setModulesPanel(ModulesPanel modulesPanel) {
        this.modulesPanel = modulesPanel;
    }

    public ModulesPanel getModulesPanel() {
        return modulesPanel;
    }

    void updateToolWindowScrollPane() {
        final LearnToolWindow myLearnToolWindow = LearnToolWindowFactory.Companion.getMyLearnToolWindow();
        if (myLearnToolWindow == null) return;
        final JBScrollPane scrollPane = myLearnToolWindow.getScrollPane();
        scrollPane.getViewport().revalidate();
        scrollPane.getViewport().repaint();
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    public String getLearnProjectPath() {
        return myState.learnProjectPath;
    }

    public void setLearnProjectPath(String learnProjectPath) {
        myState.learnProjectPath = learnProjectPath;
    }

    public void updateModules() {
        Module[] modules = getModules();
        if (modules == null) return;
        for (Module module : modules) module.update();
    }

    static class State {
        public final ArrayList<Module> modules = new ArrayList<>();
        String learnProjectPath;
        GlobalLessonLog globalLessonLog = new GlobalLessonLog();
        public long lastActivityTime;

        public State() {
        }

    }


    private void addModule(Module module) {
        myState.modules.add(module);
    }

    @Nullable
    public Module[] getModules() {
        if (myState == null) return null;
        if (myState.modules == null) return null;

        return myState.modules.toArray(new Module[myState.modules.size()]);
    }

    GlobalLessonLog getGlobalLessonLog() {
        return myState.globalLessonLog;
    }

    public long getLastActivityTime() {
        return myState.lastActivityTime;
    }

    public void setLastActivityTime(long time) {
        myState.lastActivityTime = time;
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState.learnProjectPath = null;
        myState.globalLessonLog = state.globalLessonLog;
        if (state.lastActivityTime == 0)
            myState.lastActivityTime = System.currentTimeMillis();
        else
            myState.lastActivityTime = state.lastActivityTime;

        if (state.modules == null || state.modules.size() == 0) {
            try {
                initModules();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {

            for (Module module : myState.modules) {
                if (state.modules.contains(module)) {
                    final Module moduleFromPersistentState = state.modules.get(state.modules.indexOf(module));
                    for (Lesson lesson : module.getLessons()) {
                        if (moduleFromPersistentState.getLessons().contains(lesson)) {
                            final Lesson lessonFromPersistentState = moduleFromPersistentState.getLessons().get(moduleFromPersistentState.getLessons().indexOf(lesson));
                            lesson.setPassed(lessonFromPersistentState.getPassed());
                        }
                    }
                }
            }
        }
    }

    public void updateToolWindow(@NotNull final Project project) {
        final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        String learnToolWindow = LearnToolWindowFactory.Companion.getLEARN_TOOL_WINDOW();
        windowManager.getToolWindow(learnToolWindow).getContentManager().removeAllContents(false);

        LearnToolWindowFactory factory = new LearnToolWindowFactory();
        factory.createToolWindowContent(project, windowManager.getToolWindow(learnToolWindow));
    }

    public void setLessonView() {
        final LearnToolWindow myLearnToolWindow = LearnToolWindowFactory.Companion.getMyLearnToolWindow();
        assert myLearnToolWindow != null;
        final JBScrollPane scrollPane = myLearnToolWindow.getScrollPane();
        assert scrollPane != null;
        scrollPane.setViewportView(getLearnPanel());
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    public void setModulesView() {
        ModulesPanel modulesPanel = getModulesPanel();
        modulesPanel.updateMainPanel();
        final LearnToolWindow myLearnToolWindow = LearnToolWindowFactory.Companion.getMyLearnToolWindow();
        assert myLearnToolWindow != null;
        final JBScrollPane scrollPane = myLearnToolWindow.getScrollPane();
        assert scrollPane != null;
        scrollPane.setViewportView(modulesPanel);
        scrollPane.revalidate();
        scrollPane.repaint();
    }

    public int calcUnpassedLessons() {
        int result = 0;
        if (getModules() == null) return 0;
        for (Module module : getModules()) {
            for (Lesson lesson : module.getLessons()) {
                if (!lesson.getPassed()) result++;
            }
        }
        return result;
    }

    public int calcPassedLessons() {
        int result = 0;
        if (getModules() == null) return 0;
        for (Module module : getModules()) {
            for (Lesson lesson : module.getLessons()) {
                if (lesson.getPassed()) result++;
            }
        }
        return result;
    }

    /**
     * @return null if lesson has no module or it is only one lesson in module
     */
    @Nullable
    Lesson giveNextLesson(Lesson currentLesson) {
        Module module = currentLesson.getModule();
        assert module != null;
        assert module.getLessons() != null;
        List<Lesson> lessons = module.getLessons();
        int size = lessons.size();
        if (size == 1) return null;

        for (int i = 0; i < size; i++) {
            if (lessons.get(i).equals(currentLesson)) {
                if (i + 1 < size) return lessons.get(i + 1);
                else break;
            }
        }
        return null;
    }

    @Nullable
    Module giveNextModule(Lesson currentLesson) {
        Module nextModule = null;
        Module module = currentLesson.getModule();
        Module[] modules = CourseManager.getInstance().getModules();
        if (modules == null) return null;
        int size = modules.length;
        if (size == 1) return null;

        for (int i = 0; i < size; i++) {
            if (modules[i].equals(module)) {
                if (i + 1 < size) nextModule = modules[i + 1];
                break;
            }
        }
        if (nextModule == null || nextModule.getLessons().size() == 0) return null;
        return nextModule;
    }

    public int calcLessonsForLanguage(LangSupport langSupport) {
        Ref<Integer> inc = new Ref<>(0);
        Module[] inModules = getModules();
        if (inModules == null) return 0;
        Arrays.stream(inModules).forEach(module -> inc.set(inc.get() + module.filterLessonByLang(langSupport).size()));
        return inc.get();
    }

}
