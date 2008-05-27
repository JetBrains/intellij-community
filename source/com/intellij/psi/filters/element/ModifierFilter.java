package com.intellij.psi.filters.element;

import com.intellij.psi.Modifier;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.filters.ClassFilter;
import org.jetbrains.annotations.NonNls;

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
  public final List<ModifierRestriction> myModifierRestrictions = new ArrayList<ModifierRestriction>();

  private ModifierFilter(){
    super(PsiModifierListOwner.class);
  }

  public ModifierFilter(@Modifier String modifier, boolean hasToBe){
    this();
    addModiferRestriction(modifier, hasToBe);
  }

  public ModifierFilter(String... modifiers){
    this();
    for (@Modifier String modifier : modifiers) {
      addModiferRestriction(modifier, true);
    }
  }

  private void addModiferRestriction(@Modifier String mod, boolean hasToBe){
    myModifierRestrictions.add(new ModifierRestriction(mod, hasToBe));
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiModifierListOwner){
      final PsiModifierList list = ((PsiModifierListOwner)element).getModifierList();
      if(list == null) return true;
      for (final Object myModifierRestriction : myModifierRestrictions) {
        final ModifierRestriction psiModifer = (ModifierRestriction)myModifierRestriction;
        boolean shouldHave = psiModifer.myIsSet;
        if (shouldHave != list.hasModifierProperty(psiModifer.myModifierName)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  protected static final class ModifierRestriction{
    @Modifier public final String myModifierName;
    public final boolean myIsSet;

    ModifierRestriction(@Modifier String modifierName, boolean isSet){
      myModifierName = modifierName;
      myIsSet = isSet;
    }
  }

  public String toString(){
    @NonNls String ret = "modifiers(";
    Iterator<ModifierRestriction> iter = myModifierRestrictions.iterator();
    while(iter.hasNext()){
      final ModifierRestriction rest = iter.next();
      ret += rest.myModifierName + "=" + rest.myIsSet;
      if(iter.hasNext()){
        ret += ", ";
      }
    }
    ret += ")";
    return ret;
  }
}
