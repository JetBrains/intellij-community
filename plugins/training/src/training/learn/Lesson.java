package training.learn;

import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import training.learn.exceptons.BadLessonException;
import training.learn.exceptons.BadModuleException;
import training.learn.exceptons.LessonIsOpenedException;
import training.learn.exceptons.NoProjectException;
import training.learn.log.LessonLog;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Created by karashevich on 29/01/15.
 */
public class Lesson {

    private Scenario scn;
    private String name;
    private String targetPath;
    private ArrayList<LessonListener> lessonListeners;
    private Module parentModule;
    private ArrayList<MyPair> statistic = new ArrayList<>();
    private short exerciseCount;

    private String lang;

    private boolean passed;
    private boolean isOpen;

    /*Log lesson metrics*/
    private LessonLog lessonLog;

    public boolean getPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public ArrayList<MyPair> getStatistic() {
        return statistic;
    }

    public void setStatistic(ArrayList<MyPair> kpis) {
        this.statistic = kpis;
    }

    public ArrayList<LessonListener> getLessonListeners() {
        return lessonListeners;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @AbstractCollection
    public void setScn(Scenario scn) {
        this.scn = scn;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    @NotNull
    public String getLang(){
        return getScn().getLang();
    }


    public Lesson(){
        passed = false;
        lessonLog = new LessonLog(this);
        lessonListeners = new ArrayList<>();
        exerciseCount = 0;
    }

    public Lesson(Scenario scenario, boolean passed, @Nullable Module module) throws BadLessonException {

        scn = scenario;
        name = scn.getName();

        this.passed = passed;
        lessonListeners = new ArrayList<>();
        parentModule = module;

        isOpen = false;

    }

    @Deprecated
    public void open(Dimension infoPanelDimension) throws IOException, FontFormatException, LessonIsOpenedException {
        //init infoPanel, check that Lesson has not opened yet
        if (isOpen) throw new LessonIsOpenedException(this.getName() + "is opened");
        onStart();

        isOpen = true;
    }


    public void open() throws NoProjectException, BadLessonException, ExecutionException, LessonIsOpenedException, IOException, FontFormatException, InterruptedException, BadModuleException {
        Project currentProject = CourseManager.getInstance().getCurrentProject();
        if (currentProject == null) {
            currentProject = CourseManager.getInstance().getLearnProject();
        }
        if (currentProject == null) throw new NoProjectException();
        CourseManager.getInstance().openLesson(currentProject, this);
    }

    public void open(Project projectWhereToOpenLesson) throws NoProjectException, BadLessonException, ExecutionException, LessonIsOpenedException, IOException, FontFormatException, InterruptedException, BadModuleException {
        CourseManager.getInstance().openLesson(projectWhereToOpenLesson, this);
    }

    public void close(){
        isOpen = false;
        onClose();
    }



    public boolean isOpen() {return isOpen;}

    public LessonLog getLessonLog(){
        return lessonLog;
    }

    Scenario getScn(){
        return scn;
    }

    @Nullable
    public String getTargetPath() {
        return targetPath;
    }

    @Nullable
    public Module getModule() {return parentModule;}

    public short getExerciseCount() {
        return exerciseCount;
    }

    //Listeners
    public void addLessonListener(LessonListener lessonListener){
        if (lessonListeners == null) lessonListeners = new ArrayList<>();

        lessonListeners.add(lessonListener);
    }

    public void removeLessonListener(LessonListener lessonListener) {
        if (lessonListeners.contains(lessonListener)) {
            lessonListeners.remove(lessonListener);
        }
    }

    public void onStart(){
        lessonLog = new LessonLog(this);
        lessonLog.log("Lesson started");
        exerciseCount = 0;
        statistic.add(new MyPair("started", System.currentTimeMillis()));

        lessonLog.resetCounter();
        if (lessonListeners == null) lessonListeners = new ArrayList<>();

        for (LessonListener lessonListener : lessonListeners) {
            lessonListener.lessonStarted(this);
        }
    }

    private void onItemPassed(){
        statistic.add(new MyPair("passed item #" + exerciseCount, System.currentTimeMillis()));
        exerciseCount++;
    }

    private void onClose(){
//        for (LessonListener lessonListener : lessonListeners) {
//            lessonListener.lessonClosed(this);
//        }

        lessonListeners.clear();
        statistic.add(new MyPair("closed", System.currentTimeMillis()));

    }

    //call onPass handlers in lessonListeners
    private void onPass(){
        lessonLog.log("Lesson passed");
        statistic.add(new MyPair("finished", System.currentTimeMillis()));
        CourseManager.getInstance().getGlobalLessonLog().commitSession(this);

        for (LessonListener lessonListener : lessonListeners) {
            lessonListener.lessonPassed(this);
        }

    }

    public void onNextLesson() throws BadLessonException, ExecutionException, IOException, FontFormatException, InterruptedException, BadModuleException, LessonIsOpenedException {
        for (LessonListener lessonListener : lessonListeners) {
            lessonListener.lessonNext(this);
        }
    }

    public void pass(){
        setPassed(true);
        onPass();
    }

    public void passItem(){
        onItemPassed();
    }

    class EditorParameters{
        final static String PROJECT_TREE = "projectTree";
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o instanceof Lesson && this.getName() != null && ((Lesson) o).getName() != null && ((Lesson) o).getName().equals(this.getName());
    }

}
