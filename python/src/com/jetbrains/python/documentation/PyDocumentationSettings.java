package com.jetbrains.python.documentation;

import com.intellij.openapi.components.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
@State(name = "PyDocumentationSettings",
       storages = {@Storage(file = "$MODULE_FILE$")}
)
public class PyDocumentationSettings implements PersistentStateComponent<PyDocumentationSettings> {
  public String myDocStringFormat = "";
  public boolean analyzeDoctest = true;

  public boolean isEpydocFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.EPYTEXT);
  }

  public boolean isReSTFormat(PsiFile file) {
    return isFormat(file, DocStringFormat.REST);
  }

  public boolean isPlain(PsiFile file) {
    return isFormat(file, DocStringFormat.PLAIN);
  }

  private boolean isFormat(PsiFile file, final String format) {
    if (file instanceof PyFile) {
      PyTargetExpression expr = ((PyFile) file).findTopLevelAttribute(PyNames.DOCFORMAT);
      if (expr != null) {
        String docformat = PyPsiUtils.strValue(expr.findAssignedValue());
        if (docformat != null) {
          final List<String> words = StringUtil.split(docformat, " ");
          return words.size() > 0 && format.equalsIgnoreCase(words.get(0));
        }
      }
    }
    return format.equalsIgnoreCase(myDocStringFormat);
  }

  public static PyDocumentationSettings getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, PyDocumentationSettings.class);
  }

  public void setFormat(String format) {
    myDocStringFormat = format;
  }

  @Transient
  public String getFormat() {
    return myDocStringFormat;
  }

  @Override
  public PyDocumentationSettings getState() {
    return this;
  }

  @Override
  public void loadState(PyDocumentationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
