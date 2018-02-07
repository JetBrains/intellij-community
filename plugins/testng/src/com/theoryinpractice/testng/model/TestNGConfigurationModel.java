/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.model;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationEditor;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.util.LinkedHashSet;

/**
 * @author Hani Suleiman
 */
public class TestNGConfigurationModel
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");

    private TestNGConfigurationEditor editor;
    private TestType type;
    private final Object[] typeDocuments = new Object[6];
    private final Document propertiesFileDocument = new PlainDocument();
    private final Document outputDirectoryDocument = new PlainDocument();
    private final Project project;

    public TestNGConfigurationModel(Project project) {
        type = TestType.CLASS;
        for (int i = 3; i < typeDocuments.length; i++)
            typeDocuments[i] = new PlainDocument();

        this.project = project;
    }

    public void setDocument(int type, Object doc) {
      typeDocuments[type] = doc;
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

    public Object getDocument(int index) {
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
        if (isGenerated && !JavaExecutionUtil.isNewName(config.getName())) {
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
        } else if (TestType.METHOD == type || TestType.CLASS == type || TestType.SOURCE == type) {
            String className = getText(TestType.CLASS);
            data.GROUP_NAME = "";
            data.SUITE_NAME = "";
            if (TestType.METHOD == type || TestType.SOURCE == type)
                data.METHOD_NAME = getText(TestType.METHOD);

            PsiClass psiClass = !getProject().isDefault() && !StringUtil.isEmptyOrSpaces(className) ? JUnitUtil.findPsiClass(className, module, getProject()) : null;
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
        else if (TestType.PATTERN == type) {
          final LinkedHashSet<String> set = new LinkedHashSet<>();
          final String[] patterns = getText(TestType.PATTERN).split("\\|\\|");
          for (String pattern : patterns) {
            if (pattern.length() > 0) {
              set.add(pattern);
            }
          }
          data.setPatterns(set);
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

    private String getText(TestType testType, Object[] documents) {
        Object document = documents[testType.getValue()];
      if (document instanceof PlainDocument) {
        try {
            return ((PlainDocument)document).getText(0, ((PlainDocument)document).getLength());
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
      }
      return ((com.intellij.openapi.editor.Document)document).getText();
    }

    public void reset(TestNGConfiguration config) {
        TestData data = config.getPersistantData();
        setType(data.TEST_OBJECT);
        setTypeValue(TestType.PACKAGE, data.getPackageName());
        setTypeValue(TestType.CLASS, data.getMainClassName());
        setTypeValue(TestType.METHOD, data.getMethodName());
        setTypeValue(TestType.GROUP, data.getGroupName());
        setTypeValue(TestType.SUITE, data.getSuiteName());
        setTypeValue(TestType.PATTERN, StringUtil.join(data.getPatterns(), "||"));

        setDocumentText(propertiesFileDocument, data.getPropertiesFile());
        setDocumentText(outputDirectoryDocument, data.getOutputDirectory());
    }

    private void setTypeValue(TestType type, String value) {
        setTypeValue(type, value, typeDocuments);
    }

    private void setTypeValue(TestType type, String value, Object[] documents) {
        Object document = documents[type.getValue()];
        setDocumentText(document, value);
    }

    private void setDocumentText(final Object document, final String value) {
      if (document instanceof PlainDocument) {
        try {
          ((PlainDocument)document).remove(0, ((PlainDocument)document).getLength());
          ((PlainDocument)document).insertString(0, value, null);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
      }  else {
        WriteCommandAction.runWriteCommandAction(project, () -> ((com.intellij.openapi.editor.Document)document)
          .replaceString(0, ((com.intellij.openapi.editor.Document)document).getTextLength(), value));
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
