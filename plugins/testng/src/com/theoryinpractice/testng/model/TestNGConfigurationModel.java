package com.theoryinpractice.testng.model;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ExecutionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

/**
 * @author Hani Suleiman Date: Jul 21, 2005 Time: 1:20:14 PM
 */
public class TestNGConfigurationModel
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");

    private TestNGConfigurationEditor editor;
    private TestType type;
    private final Document typeDocuments[] = new Document[5];
    private final Document propertiesFileDocument = new PlainDocument();
    private final Document outputDirectoryDocument = new PlainDocument();
    private final Project project;

    public TestNGConfigurationModel(Project project) {
        type = TestType.INVALID;
        for (int i = 0; i < typeDocuments.length; i++)
            typeDocuments[i] = new PlainDocument();

        this.project = project;
    }

    public void setType(TestType type) {
        if (type == this.type)
            return;

        this.type = type;
        updateEditorType(type);
    }

    private void updateEditorType(TestType type) {
        editor.onTypeChanged(type);
    }

    public void setListener(TestNGConfigurationEditor editor) {
        this.editor = editor;
    }

    public Document getDocument(int index) {
        return typeDocuments[index];
    }

    public Document getPropertiesFileDocument() {
        return propertiesFileDocument;
    }

    public Document getOutputDirectoryDocument() {
        return outputDirectoryDocument;
    }

    public Project getProject() {
        return project;
    }

    public void apply(Module module, TestNGConfiguration config) {
        boolean isGenerated = config.isGeneratedName();
        apply(config.getPersistantData(), module);
        if (isGenerated && !ExecutionUtil.isNewName(config.getName())) {
            config.setGeneratedName();
        }
    }

    private void apply(TestData data, Module module) {
        data.TEST_OBJECT = type.getType();
        if (TestType.GROUP == type) {
            data.GROUP_NAME = getText(TestType.GROUP);
            data.PACKAGE_NAME = "";
            data.MAIN_CLASS_NAME = "";
            data.METHOD_NAME = "";
            data.SUITE_NAME = "";
        } else if (TestType.PACKAGE == type) {
            data.PACKAGE_NAME = getText(TestType.PACKAGE);
            data.GROUP_NAME = "";
            data.MAIN_CLASS_NAME = "";
            data.METHOD_NAME = "";
            data.SUITE_NAME = "";
        } else if (TestType.METHOD == type || TestType.CLASS == type) {
            String className = getText(TestType.CLASS);
            data.GROUP_NAME = "";
            data.SUITE_NAME = "";
            if (TestType.METHOD == type)
                data.METHOD_NAME = getText(TestType.METHOD);

            PsiClass psiClass = JUnitUtil.findPsiClass(className, module, getProject(), false);
            if (psiClass != null && psiClass.isValid())
                data.setMainClass(psiClass);
            else
                data.MAIN_CLASS_NAME = className;

        } else if (TestType.SUITE == type) {
            data.SUITE_NAME = getText(TestType.SUITE);
            data.PACKAGE_NAME = "";
            data.GROUP_NAME = "";
            data.MAIN_CLASS_NAME = "";
            data.METHOD_NAME = "";
        }

        try {
            data.PROPERTIES_FILE = propertiesFileDocument.getText(0, propertiesFileDocument.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        try {
            data.OUTPUT_DIRECTORY = outputDirectoryDocument.getText(0, outputDirectoryDocument.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private String getText(TestType type) {
        return getText(type, typeDocuments);
    }

    private String getText(TestType testType, Document documents[]) {
        Document document = documents[testType.getValue()];
        try {
            return document.getText(0, document.getLength());
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    public void reset(TestNGConfiguration config) {
        TestData data = config.getPersistantData();
        setType(data.TEST_OBJECT);
        setTypeValue(TestType.PACKAGE, data.getPackageName());
        setTypeValue(TestType.CLASS, data.getMainClassName());
        setTypeValue(TestType.METHOD, data.getMethodName());
        setTypeValue(TestType.GROUP, data.getGroupName());
        setTypeValue(TestType.SUITE, data.getSuiteName());

        setDocumentText(propertiesFileDocument, data.getPropertiesFile());
        setDocumentText(outputDirectoryDocument, data.getOutputDirectory());
    }

    private void setTypeValue(TestType type, String value) {
        setTypeValue(type, value, typeDocuments);
    }

    private void setTypeValue(TestType type, String value, Document documents[]) {
        Document document = documents[type.getValue()];
        setDocumentText(document, value);
    }

    private void setDocumentText(Document document, String value) {
        try {
            document.remove(0, document.getLength());
            document.insertString(0, value, null);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setType(String s) {
        try {
            setType(TestType.valueOf(s));
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Invalid test type of " + s + " found.");
            setType(TestType.CLASS);
        }
    }
}
