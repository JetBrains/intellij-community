package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 20:27:59
 * To change this template use Options | File Templates.
 */
public class JavaClassListReferenceProvider extends JavaClassReferenceProvider{
  final static Pattern pattern = Pattern.compile("([A-Za-z]\\w*\\s*(\\.\\s*[A-Za-z]\\w*\\s*)+)");

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition){
    final List results = new ArrayList();
    final Set<String> knownTopLevelPackages = new HashSet<String>();
    final List<PsiElement> defaultPackages = getDefaultPackages(position);
    final Iterator<PsiElement> iterator = defaultPackages.iterator();
    while (iterator.hasNext()) {
      final PsiElement pack = iterator.next();
      if(pack instanceof PsiPackage)
        knownTopLevelPackages.add(((PsiPackage)pack).getName());
    }

    final Matcher matcher = pattern.matcher(str);

    while(matcher.find()){
      final String identifier = matcher.group().trim();
      final int dotPosition = identifier.indexOf('.');
      if(identifier.lastIndexOf('.') != dotPosition
      || knownTopLevelPackages.contains(identifier.substring(0, identifier.indexOf('.')))){
        results.addAll(Arrays.asList(new ReferenceSet(identifier, position, offsetInPosition + matcher.start(), type){
          protected boolean isSoft(){
            return true;
          }
        }.getAllReferences()));
      }
    }
    return (GenericReference[])results.toArray(new GenericReference[results.size()]);
  }
}
