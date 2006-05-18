package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  private static final @NonNls Pattern PATTERN = Pattern.compile("([A-Za-z]\\w*\\s*(\\.\\s*[A-Za-z]\\w*\\s*)+)");

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition){
    final Set<String> knownTopLevelPackages = new HashSet<String>();
    final List<PsiElement> defaultPackages = getDefaultPackages(position);
    for (final PsiElement pack : defaultPackages) {
      if (pack instanceof PsiPackage) {
        knownTopLevelPackages.add(((PsiPackage)pack).getName());
      }
    }
    final List<PsiReference> results = new ArrayList<PsiReference>();

    final Matcher matcher = PATTERN.matcher(str);

    while(matcher.find()){
      final String identifier = matcher.group().trim();
      if(knownTopLevelPackages.contains(identifier.substring(0, identifier.indexOf('.')))){
        results.addAll(Arrays.asList(new ReferenceSet(identifier, position, offsetInPosition + matcher.start(), type, false){
          protected boolean isSoft(){
            return true;
          }
        }.getAllReferences()));
      }
    }
    return results.toArray(new PsiReference[results.size()]);
  }
}
