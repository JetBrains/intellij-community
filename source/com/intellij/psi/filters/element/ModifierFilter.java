package com.intellij.psi.filters.element;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.FilterUtil;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:29:25
 * To change this template use Options | File Templates.
 */
public class ModifierFilter extends ClassFilter{
  public List myModifierRestrictions = new ArrayList();

  public ModifierFilter(){
    super(PsiModifierListOwner.class);
  }

  public ModifierFilter(String modifier, boolean hasToBe){
    this();
    addModiferRestriction(modifier, hasToBe);
  }

  public ModifierFilter(String[] modifiers){
    this();
    for(int i = 0; i < modifiers.length; i++){
      final String modifier = modifiers[i];
      addModiferRestriction(modifier, true);
    }
  }

  public void addModiferRestriction(String mod, boolean hasToBe){
    myModifierRestrictions.add(new ModifierRestriction(mod, hasToBe));
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiModifierListOwner){
      final PsiModifierList list = ((PsiModifierListOwner)element).getModifierList();
      if(list == null) return true;
      final Iterator iter = myModifierRestrictions.iterator();
      while(iter.hasNext()){
        final ModifierRestriction psiModifer = (ModifierRestriction)iter.next();
        boolean shouldHave = psiModifer.myIsSet;
        if(shouldHave != list.hasModifierProperty(psiModifer.myModifierName))
          return false;
      }
      return true;
    }
    return false;
  }

  protected static final class ModifierRestriction{
    public String myModifierName;
    public boolean myIsSet;

    ModifierRestriction(String modifierName, boolean isSet){
      myModifierName = modifierName;
      myIsSet = isSet;
    }
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    final Iterator iter = element.getChildren("modifier", FilterUtil.FILTER_NS).iterator();

    while(iter.hasNext()){
      final Element modifierElement = (Element) iter.next();
      final String attribute = modifierElement.getAttribute("is-set").getValue();
      myModifierRestrictions.add(
        new ModifierRestriction(modifierElement.getTextTrim(),
                                attribute.equalsIgnoreCase("true")));
    }
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public String toString(){
    String ret = "modifiers(";
    Iterator iter = myModifierRestrictions.iterator();
    while(iter.hasNext()){
      final ModifierRestriction rest = (ModifierRestriction) iter.next();
      ret += rest.myModifierName + "=" + rest.myIsSet;
      if(iter.hasNext()){
        ret += ", ";
      }
    }
    ret += ")";
    return ret;
  }
}
