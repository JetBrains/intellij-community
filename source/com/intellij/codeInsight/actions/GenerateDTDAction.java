package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.05.2003
 * Time: 13:46:54
 * To change this template use Options | File Templates.
 */
public class GenerateDTDAction extends BaseCodeInsightAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.GenerateDTDAction");
  protected CodeInsightActionHandler getHandler(){
    return new CodeInsightActionHandler(){
      public void invoke(Project project, Editor editor, PsiFile file){
        if(file instanceof XmlFile && file.getVirtualFile() != null && file.getVirtualFile().isWritable()){
          final StringBuffer buffer = new StringBuffer();
          final XmlDocument document = ((XmlFile) file).getDocument();
          if(document.getRootTag() != null){
            buffer.append("<!DOCTYPE " + document.getRootTag().getName() + " [\n");
            buffer.append(XmlUtil.generateDocumentDTD(document));
            buffer.append("]>\n");
            XmlFile tempFile = null;
            try{
              tempFile = (XmlFile) file.getManager().getElementFactory().createFileFromText("dummy.xml", buffer.toString());
              document.getProlog().replace(tempFile.getDocument().getProlog());
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        }
      }

      public boolean startInWriteAction(){
        return true;
      }
    };
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file){
    return file instanceof XmlFile;
  }
}
