package training.learn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import training.editor.MouseListenerHolder;
import training.editor.actions.BlockCaretAction;
import training.editor.actions.LearnActions;
import training.ui.LearnBalloonBuilder;
import training.ui.Message;
import training.ui.views.LearnPanel;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by karashevich on 18/03/16.
 */
public class LessonManager {

    private Lesson myCurrentLesson;

    private static ArrayList<LearnActions> myLearnActions;
    private LearnBalloonBuilder learnBalloonBuilder;
    private static boolean mouseBlocked = false;
    private static HashSet<ActionsRecorder> actionsRecorders = new HashSet<>();
    private static Editor lastEditor;
    private static MouseListenerHolder mouseListenerHolder;

    private final int balloonDelay = 3000;

    public LessonManager(Lesson lesson, Editor editor) {
        myCurrentLesson = lesson;
        mouseBlocked = false;
        if (myLearnActions == null) myLearnActions = new ArrayList<>();
        learnBalloonBuilder = null;
        lastEditor = editor;
        mouseListenerHolder = null;
    }

    public LessonManager(){
        myCurrentLesson = null;
        mouseBlocked = false;
        if (myLearnActions == null) myLearnActions = new ArrayList<>();
        lastEditor = null;
        mouseListenerHolder = null;
    }

    private static LessonManager getInstance() {
        return ServiceManager.getService(LessonManager.class);
    }

    public static LessonManager getInstance(Lesson lesson){
        final LessonManager lessonManager = getInstance();
        lessonManager.setCurrentLesson(lesson);
        return lessonManager;
    }

    private void setCurrentLesson(Lesson lesson) {
        myCurrentLesson = lesson;
    }

    void initLesson(Editor editor) throws Exception {
        cleanEditor(); //remove mouse blocks and action recorders from last editor
        lastEditor = editor; //rearrange last editor

        LearnPanel learnPanel = CourseManager.getInstance().getLearnPanel();
        learnPanel.setLessonName(myCurrentLesson.getName());
        Module module = myCurrentLesson.getModule();
        if (module == null) throw new Exception("Unable to find module for lesson: " + myCurrentLesson);
        String moduleName = module.getName();
        learnPanel.setModuleName(moduleName);
        learnPanel.getModulePanel().init(myCurrentLesson);
        clearEditor(editor);
        clearLessonPanel();
        removeActionsRecorders();

        learnBalloonBuilder = new LearnBalloonBuilder(editor, balloonDelay, LearnBundle.INSTANCE
          .message("learn.ui.balloon.blockCaret.message"));

        if (mouseListenerHolder != null) mouseListenerHolder.restoreMouseActions(editor);

        if (myLearnActions != null) {
            for (LearnActions myLearnAction : myLearnActions) {
                myLearnAction.unregisterAction();
            }
            myLearnActions.clear();
        }

        Runnable runnable = null;
        String buttonText = null;
        Lesson lesson = CourseManager.getInstance().giveNextLesson(myCurrentLesson);
        if (lesson != null) {
//            buttonText = lesson.getName();
            runnable = () -> {
                try {
                    CourseManager.getInstance().openLesson(editor.getProject(), lesson);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
        } else {
            Module nextModule = CourseManager.getInstance().giveNextModule(myCurrentLesson);
            if (nextModule != null) {
                buttonText = nextModule.getName();
                runnable = () -> {
                    Lesson notPassedLesson = nextModule.giveNotPassedLesson();
                    if (notPassedLesson == null) {
                        try {
                            CourseManager.getInstance().openLesson(editor.getProject(), nextModule.getLessons().get(0));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            CourseManager.getInstance().openLesson(editor.getProject(), notPassedLesson);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
            }
        }

        if (runnable != null) {
            CourseManager.getInstance().getLearnPanel().setButtonSkipAction(runnable, buttonText, true);
        } else {
            CourseManager.getInstance().getLearnPanel().setButtonSkipAction(null, null, false);
        }
    }

    public void addMessage(String message){
        CourseManager.getInstance().getLearnPanel().addMessage(message);
        CourseManager.getInstance().updateToolWindowScrollPane();
    }

    public void addMessages(Message[] messages) {
        CourseManager.getInstance().getLearnPanel().addMessages(messages);
        CourseManager.getInstance().updateToolWindowScrollPane();
    }

    public void passExercise() {
        CourseManager.getInstance().getLearnPanel().setPreviousMessagesPassed();
    }

    public void passLesson(Project project, Editor editor) {
        LearnPanel learnPanel = CourseManager.getInstance().getLearnPanel();
        learnPanel.setLessonPassed();
        if(myCurrentLesson.getModule() !=null && myCurrentLesson.getModule().hasNotPassedLesson()){
            final Lesson notPassedLesson = myCurrentLesson.getModule().giveNotPassedLesson();
            learnPanel.setButtonNextAction(() -> {
                try {
                    CourseManager.getInstance().openLesson(project, notPassedLesson);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, notPassedLesson);
        } else {
            Module module = CourseManager.getInstance().giveNextModule(myCurrentLesson);
            if (module == null) {
                clearLessonPanel();
                learnPanel.setModuleName("");
                learnPanel.setLessonName(LearnBundle.INSTANCE.message("learn.ui.course.completed.caption"));
                learnPanel.addMessage(LearnBundle.INSTANCE.message("learn.ui.course.completed.description"));
                learnPanel.hideNextButton();
            }
            else {
                Lesson lesson = module.giveNotPassedLesson();
                if (lesson == null) lesson = module.getLessons().get(0);

                Lesson lessonFromNextModule = lesson;
                Module nextModule = lessonFromNextModule.getModule();
                if (nextModule == null) return;
                learnPanel.setButtonNextAction(() -> {
                    try {
                        CourseManager.getInstance().openLesson(project, lessonFromNextModule);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, lesson, LearnBundle.INSTANCE.message("learn.ui.button.next.module") + " " + nextModule.getName());
            }
        }
//        learnPanel.updateLessonPanel(myCurrentLesson);
        learnPanel.getModulePanel().updateLessons(myCurrentLesson);
    }


    public void clearEditor(Editor editor) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                if (editor != null) {
                    final Document document = editor.getDocument();
                    try {
                        document.setText("");
                    } catch (Exception e) {
                        System.err.println("Unable to update text in editor!");
                    }
                }
            }
        });
    }

    private void clearLessonPanel() {
        CourseManager.getInstance().getLearnPanel().clearLessonPanel();
    }

    private void hideButtons() {
        CourseManager.getInstance().getLearnPanel().hideButtons();
    }

    private void removeActionsRecorders(){
        for (ActionsRecorder actionsRecorder : actionsRecorders) {
            actionsRecorder.dispose();
        }
        actionsRecorders.clear();
    }

    private boolean isMouseBlocked() {
        return mouseBlocked;
    }



    private void setMouseBlocked(boolean mouseBlocked) {
        LessonManager.mouseBlocked = mouseBlocked;
    }

    public void unblockCaret() {
        ArrayList<BlockCaretAction> myBlockActions = new ArrayList<BlockCaretAction>();

        for (LearnActions myLearnAction : myLearnActions) {
            if(myLearnAction instanceof BlockCaretAction) {
                myBlockActions.add((BlockCaretAction) myLearnAction);
                myLearnAction.unregisterAction();
            }
        }

        myLearnActions.removeAll(myBlockActions);
    }


    public void blockCaret(Editor editor) {

//        try {
//            LearnUiUtil.getInstance().drawIcon(myProject, getEditor());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        for (LearnActions myLearnAction : myLearnActions) {
            if(myLearnAction instanceof BlockCaretAction) return;
        }

        BlockCaretAction blockCaretAction = new BlockCaretAction(editor);
        blockCaretAction.addActionHandler(() -> {
            try {
                showCaretBlockedBalloon();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        myLearnActions.add(blockCaretAction);
    }

    public void registerActionsRecorder(ActionsRecorder recorder){
        actionsRecorders.add(recorder);
    }

    private void showCaretBlockedBalloon() throws InterruptedException {
        learnBalloonBuilder.showBalloon();
    }

    private void cleanEditor(){
        if (lastEditor == null) return;
        if (mouseListenerHolder != null) mouseListenerHolder.restoreMouseActions(lastEditor);
        removeActionsRecorders();
        unblockCaret();
    }

    public void blockMouse(Editor editor){
        mouseListenerHolder = new MouseListenerHolder(editor);
        mouseListenerHolder.grabMouseActions(() -> {
            try {
                showCaretBlockedBalloon();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void unblockMouse(Editor editor){
        if (mouseListenerHolder != null) {
            mouseListenerHolder.restoreMouseActions(editor);
        }
    }

}
