package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.psi.PyUtil.sure;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Transforms {@code from module_name import ...} into {@code import module_name}
 * and qualifies any names imported from that module by module name.
 * <br><small>
 * User: dcheryasov
 * Date: Sep 26, 2009 9:12:28 AM
 * </small>
 */
public class ImportFromToImportIntention implements IntentionAction {

  private PyFromImportStatement myFromImportStatement = null;
  private PyReferenceExpression myModuleReference = null;
  private String myModuleName = null;

  @Nullable
  private static PyFromImportStatement findStatement(Editor editor, PsiFile file) {
    return PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFromImportStatement.class);
  }

  @NotNull
  public String getText() {
    String name = myModuleName != null? myModuleName : "...";
    return "Convert to 'import " + name + "'";
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.import");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myFromImportStatement = findStatement(editor, file);
    if (myFromImportStatement != null) {
      myModuleReference = myFromImportStatement.getImportSource();
      if (myModuleReference != null) myModuleName = PyResolveUtil.toPath(myModuleReference, ".");
      return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    assert myFromImportStatement != null : "isAvailable() must have returned true, but myFromImportStatement is null";
    try {
      sure(myModuleReference); sure(myModuleName);
      // find all unqualified references that lead to one of our import elements
      final PyImportElement[] ielts = myFromImportStatement.getImportElements();
      final Map<PsiReference, PyImportElement> references = new HashMap<PsiReference, PyImportElement>();
      PsiTreeUtil.processElements(file, new PsiElementProcessor() {
        public boolean execute(PsiElement element) {
          if (element instanceof PyReferenceExpression && PsiTreeUtil.getParentOfType(element, PyImportElement.class) == null && element.isValid()) {
            PyReferenceExpression ref = (PyReferenceExpression)element;
            if (ref.getQualifier() == null) {
              ResolveResult[] resolved = ref.multiResolve(false);
              for (ResolveResult rr : resolved) {
                if (rr.isValidResult()) {
                  for (PyImportElement ielt : ielts) {
                    if (rr.getElement() == ielt) references.put(ref, ielt);
                  }
                }
              }
            }
          }
          return true;
        }
      });

      // check that at every replacement site our topmost qualifier name is visible
      PyQualifiedExpression top_qualifier;
      PyExpression feeler = myModuleReference;
      do {
        sure(feeler instanceof PyQualifiedExpression); // if for some crazy reason module name refers to numbers, etc, no point to continue.
        top_qualifier = (PyQualifiedExpression)feeler;
        feeler = top_qualifier.getQualifier();
      } while (feeler != null);
      String top_name = top_qualifier.getName();
      List<Pair<PsiElement, PsiElement>> conflicts = new ArrayList<Pair<PsiElement, PsiElement>>();
      for (PsiReference ref : references.keySet()) {
        ResolveProcessor processor = new ResolveProcessor(top_name);
        PyResolveUtil.treeCrawlUp(processor, ref.getElement());
        PsiElement result = processor.getResult();
        if (result != null) {
          List<NameDefiner> definers = processor.getDefiners();
          if (definers != null && definers.size() > 0) {
            result = definers.get(0); // in this case, processor's result is one hop of resolution too far from what we want.
          }
          conflicts.add(new Pair<PsiElement, PsiElement>(ref.getElement(), result));
        }
      }
      if (conflicts.size() > 0) {
        Usage[] usages = new Usage[conflicts.size()];
        int i = 0;
        for (Pair<PsiElement, PsiElement> pair : conflicts) {
          usages[i] = new UUsage(pair.getFirst(), pair.getSecond(), myModuleName);
          i += 1;
        }
        UsageViewPresentation prsnt = new UsageViewPresentation();
        prsnt.setTabText("Name '" + top_name + "' obscured by local redefinitions. ");
        prsnt.setCodeUsagesString("Name '" + top_name + "' obscured. Cannot convert import.");
        prsnt.setUsagesWord("occurrence");
        prsnt.setUsagesString("occurrences");
        UsageViewManager.getInstance(project).showUsages(UsageTarget.EMPTY_ARRAY, usages, prsnt);
        return;
      }

      // add qualifiers
      Language language = myFromImportStatement.getLanguage();
      assert language instanceof PythonLanguage;
      PythonLanguage pythonLanguage = (PythonLanguage)language;
      PyElementGenerator generator = pythonLanguage.getElementGenerator();
      for (Map.Entry<PsiReference, PyImportElement> entry : references.entrySet()) {
        PsiElement referring_elt = entry.getKey().getElement();
        assert referring_elt.isValid(); // else we won't add it
        ASTNode target_node = referring_elt.getNode();
        assert target_node != null; // else it won't be valid
        PyImportElement ielt = entry.getValue();
        if (ielt.getAsName() != null) {
          // we have an alias, replace it with real name
          PyReferenceExpression refex = ielt.getImportReference();
          assert refex != null; // else we won't resolve to this ielt
          String real_name = refex.getReferencedName();
          ASTNode new_qualifier = generator.createExpressionFromText(project, real_name).getNode();
          assert new_qualifier != null;
          //ASTNode first_under_target = target_node.getFirstChildNode();
          //if (first_under_target != null) new_qualifier.addChildren(first_under_target, null, null); // save the children if any
          target_node.getTreeParent().replaceChild(target_node, new_qualifier);
          target_node = new_qualifier;
        }
        target_node.addChild(generator.createDot(project), target_node.getFirstChildNode());
        target_node.addChild(sure(generator.createFromText(project, PyReferenceExpression.class, myModuleName, new int[]{0,0}).getNode()), target_node.getFirstChildNode());
      }
      // transform the import statement
      PyImportStatement new_import = sure(generator.createFromText(project, PyImportStatement.class, "import "+myModuleName));
      ASTNode parent = sure(myFromImportStatement.getParent().getNode());
      ASTNode old_node = sure(myFromImportStatement.getNode());
      parent.replaceChild(old_node, sure(new_import.getNode()));
      //myFromImportStatement.replace(new_import);
    }
    catch (IncorrectOperationException ignored) {
      PyUtil.showBalloon(project, PyBundle.message("QFIX.action.failed"), MessageType.WARNING);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}

class UUsage implements PsiElementUsage {

  private final PsiElement myElement;
  private final PsiElement myCulprit;

  static final TextAttributes SLANTED;
  private final String myPrefix;

  static {
    SLANTED = TextAttributes.ERASE_MARKER.clone();
    SLANTED.setFontType(Font.ITALIC);
  }


  public UUsage(PsiElement element, PsiElement culprit, String prefix) {
    myElement = element;
    myCulprit = culprit;
    myPrefix = prefix;
  }

  public FileEditorLocation getLocation() {
    return null;
  }

  @NotNull
  public UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @Nullable
      public Icon getIcon() {
        return myElement.getIcon(0);
      }

      @NotNull
      public TextChunk[] getText() {
        TextChunk[] chunks = new TextChunk[3];
        PsiFile file = myElement.getContainingFile();
        int lineno = file.getViewProvider().getDocument().getLineNumber(myElement.getTextOffset());
        chunks[0] = new TextChunk(SLANTED, "(" + lineno + ") ");
        chunks[1] = new TextChunk(TextAttributes.ERASE_MARKER, myElement.getText());
        chunks[2] = new TextChunk(SLANTED, " would become " + myPrefix + "." + myElement.getText());
        return chunks;
      }

      @NotNull
      public String getPlainText() {
        return myElement.getText();
      }

      public String getTooltipText() {
        return myElement.getText();
      }
    };
  }

  public boolean isValid() {
    return true;
  }

  public boolean isReadOnly() {
    return false;
  }

  public void selectInEditor() { }

  public void highlightInEditor() { }

  public void navigate(boolean requestFocus) {
    Navigatable descr = EditSourceUtil.getDescriptor(myElement);
    if (descr != null) descr.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(myElement);
  }

  public boolean canNavigateToSource() {
    return false; 
  }

  public PsiElement getElement() {
    return myCulprit;
  }

  public boolean isNonCodeUsage() {
    return false;
  }
}