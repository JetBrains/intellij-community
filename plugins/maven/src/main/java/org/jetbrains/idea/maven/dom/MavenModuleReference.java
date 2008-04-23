package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.beans.MavenModel;
import org.jetbrains.idea.maven.project.Constants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenModuleReference implements PsiReference, LocalQuickFixProvider {
  private PsiElement myElement;
  private VirtualFile myVirtualFile;
  private PsiFile myPsiFile;
  private String myText;
  private TextRange myRange;

  public MavenModuleReference(PsiElement element, VirtualFile virtualFile, PsiFile psiFile, String text, TextRange range) {
    myElement = element;
    myPsiFile = psiFile;
    myVirtualFile = virtualFile;
    myRange = range;
    myText = text;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public String getCanonicalText() {
    return myText;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public PsiElement resolve() {
    VirtualFile baseDir = myPsiFile.getVirtualFile().getParent();
    VirtualFile file = baseDir.findFileByRelativePath(myText + "/" + Constants.POM_XML);
    
    if (file == null) return null;

    return getPsiFile(file);
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants() {
    List<DomFileElement<MavenModel>> files = DomService.getInstance().getFileElements(MavenModel.class,
                                                                                      myElement.getProject(),
                                                                                      GlobalSearchScope.projectScope(getProject()));

    List<Object> result = new ArrayList<Object>();

    for (DomFileElement<MavenModel> domFile : files) {
      VirtualFile virtualFile = domFile.getOriginalFile().getVirtualFile();
      if (virtualFile == myVirtualFile) continue;

      PsiFile psiFile = domFile.getFile();
      String modulePath = calcRelativeModulePath(virtualFile);

      LookupElement<PsiFile> lookup = LookupElementFactory.getInstance().createLookupElement(psiFile, modulePath);
      lookup.setPresentableText(modulePath);
      result.add(lookup);
    }

    return result.toArray();
  }

  private String calcRelativeModulePath(VirtualFile modulePom) {
    String result = FileUtil.getRelativePath(new File(myVirtualFile.getParent().getPath()),
                                             new File(modulePom.getPath()));

    result = FileUtil.toSystemIndependentName(result);
    result = result.substring(0, result.length() - ("/" + Constants.POM_XML).length());

    return result;
  }

  private PsiFile getPsiFile(VirtualFile file) {
    Document doc = FileDocumentManager.getInstance().getDocument(file);
    return PsiDocumentManager.getInstance(getProject()).getPsiFile(doc);
  }

  private Project getProject() {
    return myPsiFile.getProject();
  }

  public LocalQuickFix[] getQuickFixes() {
    if (myText.length() == 0 || resolve() != null) return LocalQuickFix.EMPTY_ARRAY;
    return new LocalQuickFix[] {new CreatePomFix()};
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isSoft() {
    return true;
  }

  private class CreatePomFix implements LocalQuickFix {
    @NotNull
      public String getName() {
      return DomBundle.message("fix.create.pom");
    }

    @NotNull
      public String getFamilyName() {
      return DomBundle.message("inspection.group");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor d) {
      try {
        VirtualFile modulePom = createModulePom();
        TemplateBuilder b = buildTemplate(project, modulePom);
        runTemplate(project, modulePom, b.buildTemplate());
      }
      catch (IOException e) {
        Messages.showErrorDialog(e.getMessage(), getName());
      }
    }

    private VirtualFile createModulePom() throws IOException {
      VirtualFile baseDir = myVirtualFile.getParent();
      String modulePath = PathUtil.getCanonicalPath(baseDir.getPath() + "/" + myText);
      VirtualFile moduleDir = VfsUtil.createDirectories(modulePath);
      return moduleDir.createChildData(this, Constants.POM_XML);
    }

    private TemplateBuilder buildTemplate(Project project, VirtualFile modulePom) {
      XmlFile psiFile = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText(
          Constants.POM_XML,
          StdLanguages.XML,
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
          "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
          "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
          "    <modelVersion>4.0.0</modelVersion>\n" +
          "    <groupId>xxx</groupId>\n" +
          "    <artifactId>xxx</artifactId>\n" +
          "    <version>xxx</version>\n" +
          "</project>");

      TemplateBuilder b = new TemplateBuilder(psiFile);

      XmlFile parentFile = (XmlFile)myElement.getContainingFile();
      MavenModel parentModel = DomManager.getDomManager(project).getFileElement(parentFile, MavenModel.class).getRootElement();

      String proposedArtifactId = modulePom.getParent().getName();
      String proposedGroupId = parentModel.getGroupId().getValue();
      String proposedVersion = parentModel.getVersion().getValue();

      if (proposedGroupId == null) {
        proposedGroupId = parentModel.getMavenParent().getGroupId().getValue();
        if (proposedGroupId == null) proposedGroupId = "groupId";
      }

      if (proposedVersion == null) {
        proposedVersion = parentModel.getMavenParent().getVersion().getValue();
        if (proposedVersion == null) proposedVersion = "version";
      }

      b.replaceElement(getTagValueElement(psiFile, "groupId"), new ConstantNode(proposedGroupId));
      b.replaceElement(getTagValueElement(psiFile, "artifactId"), new ConstantNode(proposedArtifactId));
      b.replaceElement(getTagValueElement(psiFile, "version"), new ConstantNode(proposedVersion));
      return b;
    }

    private XmlText getTagValueElement(XmlFile psiFile, String qname) {
      return psiFile.getDocument().getRootTag().findFirstSubTag(qname).getValue().getTextElements()[0];
    }

    private void runTemplate(Project project, VirtualFile modulePom, Template template) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, modulePom, 0);
      Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
  }
}
