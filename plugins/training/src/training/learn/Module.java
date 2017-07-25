package training.learn;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import training.lang.LangManager;
import training.lang.LangSupport;
import training.learn.exceptons.BadLessonException;
import training.learn.exceptons.BadModuleException;
import training.util.GenModuleXml;
import training.util.MyClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sergey Karashevich
 */
@Tag("course")
public class Module{

    private String modulePath;
    private String moduleDescription;

    String getModulePath() {
        return (modulePath != null ? modulePath + "/" : "");
    }

    @Nullable
    public String getDescription() {
        return moduleDescription;
    }

    public enum ModuleType {SCRATCH, PROJECT}

    //used for lessons filtered by LangManger chosen lang
    private List<Lesson> lessons;
    private ArrayList<Lesson> allLessons;

    private List<ModuleUpdateListener> moduleUpdateListeners;

    @Nullable
    private String answersPath;
    @Nullable
    private Element root = null;
    private String id;
    @NotNull
    public String name;
    public ModuleType moduleType;
    @Nullable
    private ModuleSdkType mySdkType = null;

    public enum ModuleSdkType {JAVA}

    public void setAnswersPath(@Nullable String answersPath) {
        this.answersPath = answersPath;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLessons(List<Lesson> lessons) {
        this.lessons = lessons;
    }

    public void setMySdkType(@Nullable ModuleSdkType mySdkType) {
        this.mySdkType = mySdkType;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    public void setModulePath(String modulePath) {
        this.modulePath = modulePath;
    }



    @TestOnly
    public Module(){
        name = "Test";
        lessons = new ArrayList<>();
        allLessons = new ArrayList<>();
        moduleUpdateListeners = new ArrayList<>();
    }

    private Module(@NotNull String name, @Nullable Element root) throws JDOMException, BadLessonException, BadModuleException, IOException, URISyntaxException {
        lessons = new ArrayList<>();
        allLessons = new ArrayList<>();
        moduleUpdateListeners = new ArrayList<>();
        this.name = name;
        this.root = root;
        modulePath = GenModuleXml.MODULE_MODULES_PATH;
        moduleDescription = root.getAttribute(GenModuleXml.MODULE_DESCRIPTION_ATTR) != null ? root.getAttributeValue(GenModuleXml.MODULE_DESCRIPTION_ATTR) : null;
        initLessons();
        if (root.getAttribute(GenModuleXml.MODULE_ANSWER_PATH_ATTR) != null) {
            answersPath = root.getAttribute(GenModuleXml.MODULE_ANSWER_PATH_ATTR).getValue();
        } else {
            answersPath = null;
        }
        id = root.getAttribute(GenModuleXml.MODULE_ID_ATTR).getValue();
        if (root.getAttribute(GenModuleXml.MODULE_SDK_TYPE) != null){
            mySdkType = GenModuleXml.getSdkTypeFromString(root.getAttribute(GenModuleXml.MODULE_SDK_TYPE).getValue());
        }
        final Attribute attributeFileType = root.getAttribute(GenModuleXml.MODULE_FILE_TYPE);
        if (attributeFileType != null) {
            if(attributeFileType.getValue().toUpperCase().equals(ModuleType.SCRATCH.toString().toUpperCase())) moduleType = ModuleType.SCRATCH;
            else if(attributeFileType.getValue().toUpperCase().equals(ModuleType.PROJECT.toString().toUpperCase())) moduleType = ModuleType.PROJECT;
            else throw new BadModuleException("Unable to recognise ModuleType (should be SCRATCH or PROJECT)");
        }
    }

    @NotNull
    public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
        AnAction[] actions = new AnAction[lessons.size()];
        actions = lessons.toArray(actions);
        return actions;
    }

    @Nullable
    static Module initModule(String modulePath) throws BadModuleException, BadLessonException, JDOMException, IOException, URISyntaxException {
        //load xml with lessons

        //Check DOM with Module
        Element init_root = getRootFromPath(modulePath);
        if(init_root.getAttribute(GenModuleXml.MODULE_NAME_ATTR) == null) return null;
        String init_name = init_root.getAttribute(GenModuleXml.MODULE_NAME_ATTR).getValue();

        return new Module(init_name, init_root);

    }

    static Element getRootFromPath(String pathToFile) throws JDOMException, IOException {
        InputStream is = MyClassLoader.getInstance().getResourceAsStream(pathToFile);
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(is);
        return document.getRootElement();
    }

    @Nullable
    public String getAnswersPath() {
        return answersPath;
    }


    @AbstractCollection(surroundWithTag = true)
    public List<Lesson> getLessons() {
        return lessons;
    }

    private void initLessons() throws BadModuleException, BadLessonException, JDOMException, IOException, URISyntaxException {

        assert root != null;
        name = root.getAttribute(GenModuleXml.MODULE_NAME_ATTR).getValue();

        if (root.getAttribute(GenModuleXml.MODULE_LESSONS_PATH_ATTR) != null) {

            //retrieve list of xml files inside lessonspath directory
            String lessonsPath = getModulePath() + root.getAttribute(GenModuleXml.MODULE_LESSONS_PATH_ATTR).getValue();

            for (Element lessonElement : root.getChildren()) {
                if (!lessonElement.getName().equals(GenModuleXml.MODULE_LESSON_ELEMENT))
                    throw new BadModuleException("Module file is corrupted or cannot be read properly");

                String lessonFilename = lessonElement.getAttributeValue(GenModuleXml.MODULE_LESSON_FILENAME_ATTR);
                String lessonPath = lessonsPath + lessonFilename;
                try {
                    Scenario scn = new Scenario(lessonPath);
                    Lesson lesson = new Lesson(scn, false, this);
                    allLessons.add(lesson);
                } catch (JDOMException e) {
                    //Lesson file is corrupted
                    throw new BadLessonException("Probably lesson file is corrupted: " + lessonPath + " JDOMException:" + e);
                } catch (IOException e) {
                    //Lesson file cannot be read
                    throw new BadLessonException("Probably lesson file cannot be read: " + lessonPath);
                }
            }
        }
        lessons = filterLessonsByCurrentLang();
    }


    private List<Lesson> filterLessonsByCurrentLang() {
        LangManager langManager = LangManager.Companion.getInstance();
        if (langManager.isLangUndefined()) return allLessons;
        //noinspection ConstantConditions
        return filterLessonByLang(langManager.getLangSupport());
    }

    public List<Lesson> filterLessonByLang(LangSupport langSupport) {
        return allLessons.stream().filter(lesson -> langSupport.acceptLang(lesson.getLang())).collect(Collectors.toList());
    }

    @Nullable
    public Lesson giveNotPassedLesson() {
        for (Lesson lesson : lessons) {
            if (!lesson.getPassed()) return lesson;
        }
        return null;
    }

    @Nullable
    public Lesson giveNotPassedAndNotOpenedLesson() {
        for (Lesson lesson : lessons) {
            if (!lesson.getPassed() && !lesson.isOpen()) return lesson;
        }
        return null;
    }

    public boolean hasNotPassedLesson() {
        for (Lesson lesson : lessons) {
            if (!lesson.getPassed()) return true;
        }
        return false;
    }

    public String getId(){
        return id;
    }


    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getNameWithoutWhitespaces(){
        return name.replaceAll("\\s+", "");
    }

    ModuleSdkType getSdkType() {
         return mySdkType;
    }

    public boolean equals(Object o) {
        if(o == null) return false;
        if(!(o instanceof Module)) return false;
        return ((Module) o).getName().equals(this.getName());

    }

    @Nullable
    Element getModuleRoot(){
        return root;
    }

    public void update(){
        lessons = filterLessonsByCurrentLang();
        moduleUpdateListeners.forEach(ModuleUpdateListener::onUpdate);
    }

    public void registerListener(ModuleUpdateListener moduleUpdateListener) {
        moduleUpdateListeners.add(moduleUpdateListener);
    }

    public void removeAllListeners() {
        moduleUpdateListeners.clear();
    }

    public class ModuleUpdateListener implements EventListener {
        void onUpdate() { }
    }
}
