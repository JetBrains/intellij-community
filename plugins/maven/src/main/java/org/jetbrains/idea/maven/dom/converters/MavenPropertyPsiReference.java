package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.project.MavenParentProjectFileProcessor;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Set;

public class MavenPropertyPsiReference extends MavenPsiReference {
  public MavenPropertyPsiReference(PsiElement element, String text, int from) {
    super(element, text, TextRange.from(from, text.length()));
  }

  public PsiElement resolve() {
    PsiElement result = doResolve();
    if (result == null) return result;

    XmlTagChild[] children = ((XmlTag)result).getValue().getChildren();
    if (children.length != 1) return result;

    return new PsiElementWrapper(result, children[0]);
  }

  private PsiElement doResolve() {
    if (myText.startsWith("project.") || myText.startsWith("pom.")) {
      String path = myText.startsWith("pom.")
                    ? "project." + myText.substring("pom.".length())
                    : myText;
      DomElement domElement = DomUtil.getDomElement(myElement);
      DomFileElement<DomElement> fileElement = DomUtil.getFileElement(domElement);
      return resolveModelProperty(fileElement, path, new THashSet<DomFileElement>());
    }
    return null;
  }

  private PsiElement resolveModelProperty(DomFileElement<? extends DomElement> fileElement, String path, Set<DomFileElement> recursionGuard) {
    if (recursionGuard.contains(fileElement)) return null;
    recursionGuard.add(fileElement);

    XmlTag result = MavenDomUtil.findTag(fileElement, path);
    if (result != null) return result;

    if (path.equals("project.groupId") || path.equals("project.version")) {
      path = path.replace("project.", "project.parent.");
      return MavenDomUtil.findTag(fileElement, path);
    }

    MavenDomProjectModel projectDomModel = (MavenDomProjectModel)fileElement.getRootElement();
    VirtualFile projectFile = fileElement.getFile().getVirtualFile();

    MavenDomParent parent = projectDomModel.getMavenParent();
    if (!DomUtil.hasXml(parent)) return null;

    String parentGroupId = parent.getGroupId().getValue();
    String parentArtifactId = parent.getArtifactId().getValue();
    String parentVersion = parent.getVersion().getValue();
    String parentRelativePath = parent.getRelativePath().getStringValue();
    if (StringUtil.isEmptyOrSpaces(parentRelativePath)) parentRelativePath = "../pom.xml";
    MavenId parentId = new MavenId(parentGroupId, parentArtifactId, parentVersion);

    final MavenProjectsManager manager = MavenProjectsManager.getInstance(myElement.getProject());
    DomFileElement<? extends DomElement> parentFileElement = new MavenParentProjectFileProcessor<DomFileElement<? extends DomElement>>() {
      protected VirtualFile findManagedFile(MavenId id) {
        MavenProject project = manager.findProject(id);
        return project == null ? null : project.getFile();
      }

      protected DomFileElement<? extends DomElement> doProcessParent(VirtualFile parentFile) {
        return MavenDomUtil.getMavenDomProjectFile(myElement.getProject(), parentFile);
      }
    }.process(projectFile, parentId, parentRelativePath, manager.getLocalRepository());

    if (parentFileElement != null) {
      return resolveModelProperty(parentFileElement, path, recursionGuard);
    }
    return result;
  }

  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
