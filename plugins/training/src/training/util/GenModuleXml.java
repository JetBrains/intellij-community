package training.util;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import training.learn.Module;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by karashevich on 11/08/15.
 */
public class GenModuleXml {

    private final static String MODULE_ALLMODULE_ATTR = "modules";
    public final static String MODULE_ALLMODULE_FILENAME = "modules.xml";
    public final static String MODULE_MODULES_PATH = "modules";
    public final static String MODULE_TYPE_ATTR = "module";

    public final static String MODULE_NAME_ATTR = "name";
    private final static String MODULE_XML_VER_ATTR = "version";
    public final static String MODULE_ID_ATTR = "id";
    private final static String MODULE_XML_VERSION = "0.3";
    public final static String MODULE_LESSON_ELEMENT = "lesson";
    public final static String MODULE_ANSWER_PATH_ATTR = "answerPath";
    public final static String MODULE_DESCRIPTION_ATTR = "description";
    public final static String MODULE_SDK_TYPE = "sdkType";
    public final static String MODULE_FILE_TYPE = "fileType";

    public final static String MODULE_LESSONS_PATH_ATTR = "lessonsPath";
    public final static String MODULE_LESSON_FILENAME_ATTR = "filename";
    public final static String MODULE_LESSON_SOLUTION = "solution";

    public static void gen(String moduleName, String id, String path) throws URISyntaxException {
        try {

            Element module = new Element(MODULE_TYPE_ATTR);
            module.setAttribute(MODULE_NAME_ATTR, moduleName);
            module.setAttribute(MODULE_LESSONS_PATH_ATTR, path);
            module.setAttribute(MODULE_XML_VER_ATTR, MODULE_XML_VERSION);
            module.setAttribute(MODULE_ID_ATTR, id);
            Document doc = new Document(module);
            doc.setRootElement(module);

            File dir = new File(GenModuleXml.class.getResource("/data/" + path).toURI());

            if (dir.listFiles() == null) return;
            for (File file : dir.listFiles()) {
                if (file.isFile()) {
                    Element lesson = new Element(MODULE_LESSON_ELEMENT);
                    lesson.setAttribute(MODULE_LESSON_FILENAME_ATTR, file.getName());
                    doc.getRootElement().addContent(lesson);
                }
            }
            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat(Format.getPrettyFormat());
            String dataPath = GenModuleXml.class.getResource("/data/").getPath();
            File outputFile = new File(dataPath + moduleName + ".xml");
            outputFile.createNewFile();
            xmlOutput.output(doc, new FileWriter(outputFile));

        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    private static void genModules() throws URISyntaxException, IOException {

        Element modules = new Element(MODULE_ALLMODULE_ATTR);
        modules.setAttribute(MODULE_XML_VER_ATTR, MODULE_XML_VERSION);
        Document doc = new Document(modules);
        doc.setRootElement(modules);



        File dir = new File(GenModuleXml.class.getResource("/data/").toURI());
        if (dir.listFiles() == null) return;

        for (File file : dir.listFiles()) {
            if (file.isFile()) {
                if (!file.getName().equals(MODULE_ALLMODULE_FILENAME)) {
                    String name = file.getName();
                    doc.getRootElement().addContent((new Element(MODULE_TYPE_ATTR)).setAttribute(MODULE_NAME_ATTR, name));
                }
            }
        }

        XMLOutputter xmlOutput = new XMLOutputter();
        xmlOutput.setFormat(Format.getPrettyFormat());
        String dataPath = GenModuleXml.class.getResource("/data/").getPath();
        File outputFile = new File(dataPath + MODULE_ALLMODULE_FILENAME);
        outputFile.createNewFile();
        xmlOutput.output(doc, new FileWriter(outputFile));
    }

    public static void main(String[] args) throws URISyntaxException, IOException {
//        gen("Completions", "completions", "Completions/");
//        gen("Refactorings", "refactorings", "Refactorings/");
//        gen("Navigation", "navigation", "Navigation/");
        genModules();
    }

    public static Module.ModuleSdkType getSdkTypeFromString(String string) {
        for (Module.ModuleSdkType moduleSdkType : Module.ModuleSdkType.values()) {
            if(moduleSdkType.toString().equals(string)) return moduleSdkType;
        }
        return null;
    }
}
