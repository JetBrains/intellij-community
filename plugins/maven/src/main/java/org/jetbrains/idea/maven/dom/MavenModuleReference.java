package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.PathUtil;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenModuleReference extends MavenPsiReference implements LocalQuickFixProvider {
  final private VirtualFile myVirtualFile;
  final private PsiFile myPsiFile;

  public MavenModuleReference(PsiElement element,
                              String originalText,
                              String resolvedText,
                              TextRange range,
                              VirtualFile virtualFile,
                              PsiFile psiFile) {
    super(element, originalText, resolvedText, range);
    myPsiFile = psiFile;
    myVirtualFile = virtualFile;
  }

  public PsiElement resolve() {
    VirtualFile baseDir = myPsiFile.getVirtualFile().getParent();
    String relPath = FileUtil.toSystemIndependentName(myResolvedText + "/" + MavenConstants.POM_XML);
    VirtualFile file = baseDir.findFileByRelativePath(relPath);

    if (file == null) return null;

    return getPsiFile(file);
  }

  public Object[] getVariants() {
    List<DomFileElement<MavenDomProjectModel>> files = PomDescriptor.collectProjectPoms(getProject());

    List<Object> result = new ArrayList<Object>();

    for (DomFileElement<MavenDomProjectModel> eachDomFile : files) {
      VirtualFile eachVFile = eachDomFile.getOriginalFile().getVirtualFile();
      if (eachVFile == myVirtualFile) continue;

      PsiFile psiFile = eachDomFile.getFile();
      String modulePath = calcRelativeModulePath(myVirtualFile, eachVFile);

      MutableLookupElement<PsiFile> lookup = LookupElementFactory.getInstance().createLookupElement(psiFile, modulePath);
      lookup.setPresentableText(modulePath);
      result.add(lookup);
    }

    return result.toArray();
  }

  public static String calcRelativeModulePath(VirtualFile parentPom, VirtualFile modulePom) {
    String result = MavenDomUtil.calcRelativePath(parentPom.getParent(), modulePom);
    int to = result.length() - ("/" + MavenConstants.POM_XML).length();
    if (to < 0) {
      // todo IDEADEV-35440
      throw new RuntimeException("Filed to calculate relative path for:" +
                                 "\nparentPom: " + parentPom + "(valid: " + parentPom.isValid() + ")" +
                                 "\nmodulePom: " + modulePom + "(valid: " + modulePom.isValid() + ")" +
                                 "\nequals:" + parentPom.equals(modulePom));
    }
    return result.substring(0, to);
  }

  private PsiFile getPsiFile(VirtualFile file) {
    return PsiManager.getInstance(getProject()).findFile(file);
  }

  private Project getProject() {
    return myPsiFile.getProject();
  }

  public LocalQuickFix[] getQuickFixes() {
    if (myResolvedText.length() == 0 || resolve() != null) return LocalQuickFix.EMPTY_ARRAY;
    return new LocalQuickFix[]{new CreatePomFix()};
  }

  private class CreatePomFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return MavenDomBundle.message("fix.create.pom");
    }

    @NotNull
    public String getFamilyName() {
      return MavenDomBundle.message("inspection.group");
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
      String modulePath = PathUtil.getCanonicalPath(baseDir.getPath() + "/" + myResolvedText);
      VirtualFile moduleDir = VfsUtil.createDirectories(modulePath);
      return moduleDir.createChildData(this, MavenConstants.POM_XML);
    }

    private TemplateBuilder buildTemplate(Project project, VirtualFile modulePomFile) {
      MavenId id = PomDescriptor.describe(PomDescriptor.getPom(myPsiFile));

      String groupId = id.getGroupId() == null ? "groupId" : id.getGroupId();
      String artifactId = modulePomFile.getParent().getName();
      String version= id.getVersion() == null ? "version" : id.getVersion();

      XmlFile psiFile = (XmlFile)PsiFileFactory.getInstance(project).createFileFromText(
        MavenConstants.POM_XML,
        StdLanguages.XML,
        MavenUtil.makeFileContent(new MavenId(groupId, artifactId, version)));

      TemplateBuilder b = new TemplateBuilder(psiFile);

      b.replaceElement(getTagValueElement(psiFile, "groupId"), new ConstantNode(groupId));
      b.replaceElement(getTagValueElement(psiFile, "artifactId"), new ConstantNode(artifactId));
      b.replaceElement(getTagValueElement(psiFile, "version"), new ConstantNode(version));

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
