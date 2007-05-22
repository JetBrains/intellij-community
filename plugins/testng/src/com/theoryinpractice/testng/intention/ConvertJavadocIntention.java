package com.theoryinpractice.testng.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.Intentions;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 4:18:11 PM
 */
public class ConvertJavadocIntention extends AbstractProjectIntention
{
    public String getText() {
        return "Convert TestNG Javadoc to 1.5 annotations";
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        if(!file.isWritable()) return false;
        if(!(file instanceof PsiJavaFile)) return false;
        LanguageLevel languageLevel = ((PsiJavaFile) file).getLanguageLevel();
        if(languageLevel == LanguageLevel.JDK_1_3 || languageLevel == LanguageLevel.JDK_1_4) return false;
        JavadocVisitor visitor = new JavadocVisitor();
        file.accept(visitor);
        return visitor.hasAnnotations;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiJavaFile javaFile = (PsiJavaFile)file;
        PsiElementFactory factory = file.getManager().getElementFactory();
        javaFile.getImportList().add(factory.createImportStatementOnDemand("org.testng.annotations"));
        for(PsiClass psiClass : javaFile.getClasses()) {
            for(PsiMethod method : psiClass.getMethods()) {
                Intentions.convertJavadocToAnnotation(method);
            }
        }
    }

    public boolean startInWriteAction() {
        return true;
    }

    private static class JavadocVisitor extends PsiRecursiveElementVisitor
    {
        boolean hasAnnotations;

        @Override
        public void visitDocTag(PsiDocTag tag) {
            if(hasAnnotations) return;
            super.visitDocTag(tag);
            if(tag.getName().startsWith("testng."))
                hasAnnotations = true;
        }
    }
}
