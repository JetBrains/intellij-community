package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class LombokLightReferenceListBuilder extends LightElement implements PsiReferenceList {
  private final List<PsiJavaCodeReferenceElement> myRefs = new ArrayList<PsiJavaCodeReferenceElement>();
  private PsiJavaCodeReferenceElement[] myCachedRefs = null;
  private PsiClassType[] myCachedTypes = null;
  private final PsiReferenceList.Role myRole;
  private final PsiElementFactory myFactory;

  public LombokLightReferenceListBuilder(PsiManager manager, PsiReferenceList.Role role) {
    this(manager, StdLanguages.JAVA, role);
  }

  public LombokLightReferenceListBuilder(PsiManager manager, Language language, PsiReferenceList.Role role) {
    super(manager, language);
    myRole = role;
    myFactory = JavaPsiFacade.getElementFactory(getProject());
  }

  public void addReference(PsiClass aClass) {
    addReference(aClass.getQualifiedName());
  }

  public void addReference(String qualifiedName) {
    final PsiJavaCodeReferenceElement ref = myFactory.createReferenceElementByFQClassName(qualifiedName, getResolveScope());
    myRefs.add(ref);
  }

  public void addReference(PsiClassType type) {
    final PsiJavaCodeReferenceElement ref = myFactory.createReferenceElementByType(type);
    myRefs.add(ref);
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    if (myCachedRefs == null) {
      if (myRefs.isEmpty()) {
        myCachedRefs = PsiJavaCodeReferenceElement.EMPTY_ARRAY;
      } else {
        myCachedRefs = ContainerUtil.toArray(myRefs, new PsiJavaCodeReferenceElement[myRefs.size()]);
      }
    }
    return myCachedRefs;
  }

  @NotNull
  @Override
  public PsiClassType[] getReferencedTypes() {
    if (myCachedTypes == null) {
      if (myRefs.isEmpty()) {
        myCachedTypes = PsiClassType.EMPTY_ARRAY;
      } else {
        final int size = myRefs.size();
        myCachedTypes = new PsiClassType[size];
        for (int i = 0; i < size; i++) {
          myCachedTypes[i] = myFactory.createType(myRefs.get(i));
        }
      }
    }

    return myCachedTypes;
  }

  @Override
  public PsiReferenceList.Role getRole() {
    return myRole;
  }

  @Override
  public String toString() {
    return "LombokLightReferenceList";
  }


}
