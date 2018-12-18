package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class EqualsAndHashCodeToStringHandler {

  private static final String TO_STRING_RANK_ANNOTATION_PARAMETER = "rank";

  public static class MemberInfo implements Comparable<MemberInfo> {
    private final PsiField psiField;
    private final PsiMethod psiMethod;
    private final String memberName;
    private final boolean defaultInclude;
    private final int rankValue;

    MemberInfo(PsiField psiField) {
      this(psiField, psiField.getName(), false);
    }

    MemberInfo(PsiField psiField, String memberName, int rankValue) {
      this(psiField, memberName, false, rankValue);
    }

    MemberInfo(PsiField psiField, String memberName, boolean defaultInclude) {
      this(psiField, memberName, defaultInclude, 0);
    }

    private MemberInfo(PsiField psiField, String memberName, boolean defaultInclude, int rankValue) {
      this.psiField = psiField;
      this.psiMethod = null;
      this.memberName = memberName;
      this.defaultInclude = defaultInclude;
      this.rankValue = rankValue;
    }

    MemberInfo(PsiMethod psiMethod, String memberName, int rankValue) {
      this.psiField = null;
      this.psiMethod = psiMethod;
      this.memberName = memberName;
      this.defaultInclude = false;
      this.rankValue = rankValue;
    }

    public PsiField getField() {
      return psiField;
    }

    public PsiMethod getMethod() {
      return psiMethod;
    }

    public String getName() {
      return memberName;
    }

    public PsiType getType() {
      if (null != psiField) {
        return psiField.getType();
      }
      return psiMethod.getReturnType();
    }

    private boolean matchDefaultIncludedFieldName(String fieldName) {
      if (null != psiField && defaultInclude) {
        return fieldName.equals(psiField.getName());
      }
      return false;
    }

    @Override
    public int compareTo(@NotNull MemberInfo other) {
      return Integer.compare(other.rankValue, rankValue);
    }
  }

  public Collection<MemberInfo> filterFields(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, boolean filterTransient, String includeAnnotationProperty) {
    final boolean explicitOf = PsiAnnotationUtil.hasDeclaredProperty(psiAnnotation, "of");
    final boolean onlyExplicitlyIncluded = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "onlyExplicitlyIncluded", false);

    final String annotationFQN = psiAnnotation.getQualifiedName();
    final String annotationIncludeFQN = annotationFQN + ".Include";
    final String annotationExcludeFQN = annotationFQN + ".Exclude";

    //Having both exclude and of generates a warning; the exclude parameter will be ignored in that case.
    final Collection<String> ofProperty;
    final Collection<String> excludeProperty;
    if (!explicitOf) {
      ofProperty = Collections.emptyList();
      excludeProperty = makeSet(PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "exclude", String.class));
    } else {
      ofProperty = makeSet(PsiAnnotationUtil.getAnnotationValues(psiAnnotation, "of", String.class));
      excludeProperty = Collections.emptyList();
    }

    final Collection<PsiMember> psiMembers = PsiClassUtil.collectClassMemberIntern(psiClass);

    final Collection<String> fieldNames2BeReplaced = new ArrayList<>();
    final List<MemberInfo> result = new ArrayList<>(psiMembers.size());

    for (PsiMember psiMember : psiMembers) {
      final PsiAnnotation includeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiMember, annotationIncludeFQN);
      if (null == includeAnnotation) {
        if (onlyExplicitlyIncluded) {
          continue;
        }
        if (!(psiMember instanceof PsiField)) {
          continue;
        }
        final PsiField psiField = (PsiField) psiMember;
        final String fieldName = psiField.getName();
        if (null == fieldName) {
          continue;
        }

        if (ofProperty.contains(fieldName)) {
          result.add(new MemberInfo(psiField));
          continue;
        } else if (explicitOf) {
          continue;
        }

        if (excludeProperty.contains(fieldName)) {
          continue;
        }

        if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        if ((filterTransient && psiField.hasModifierProperty(PsiModifier.TRANSIENT))) {
          continue;
        }
        if (fieldName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
          continue;
        }
        if (PsiAnnotationSearchUtil.isAnnotatedWith(psiField, annotationExcludeFQN)) {
          continue;
        }
        result.add(new MemberInfo(psiField, fieldName, true));
      } else {
        final String includeNameValue = PsiAnnotationUtil.getStringAnnotationValue(includeAnnotation, includeAnnotationProperty);
        final String newMemberName;
        if (StringUtil.isEmptyOrSpaces(includeNameValue)) {
          newMemberName = psiMember.getName();
        } else {
          newMemberName = includeNameValue;
        }

        if ((psiMember instanceof PsiMethod)) {
          final PsiMethod psiMethod = (PsiMethod) psiMember;
          if (0 == psiMethod.getParameterList().getParametersCount()) {
            fieldNames2BeReplaced.add(newMemberName);
            int memberRank = calcMemberRank(includeAnnotation);
            result.add(new MemberInfo(psiMethod, psiMethod.getName(), memberRank));
          }
        } else {
          int memberRank = calcMemberRank(includeAnnotation);
          result.add(new MemberInfo((PsiField) psiMember, newMemberName, memberRank));
        }
      }
    }

    for (String fieldName : fieldNames2BeReplaced) {
      // delete default-included fields with the same name as an explicit inclusion
      result.removeIf(memberInfo -> memberInfo.matchDefaultIncludedFieldName(fieldName));
    }

    result.sort(MemberInfo::compareTo);
    return result;
  }

  private int calcMemberRank(@NotNull PsiAnnotation includeAnnotation) {
    final String includeRankValue = PsiAnnotationUtil.getStringAnnotationValue(includeAnnotation, TO_STRING_RANK_ANNOTATION_PARAMETER);
    if (!StringUtil.isEmptyOrSpaces(includeRankValue)) {
      try {
        return Integer.parseInt(includeRankValue);
      } catch (NumberFormatException ignore) {
      }
    }
    return 0;
  }

  public String getMemberAccessorName(@NotNull MemberInfo memberInfo, boolean doNotUseGetters, @NotNull PsiClass psiClass) {
    final String memberAccessor;
    if (null == memberInfo.getMethod()) {
      memberAccessor = buildAttributeNameString(doNotUseGetters, memberInfo.getField(), psiClass);
    } else {
      memberAccessor = memberInfo.getName() + "()";
    }
    return memberAccessor;
  }

  private String buildAttributeNameString(boolean doNotUseGetters, @NotNull PsiField classField, @NotNull PsiClass psiClass) {
    final String fieldName = classField.getName();
    if (doNotUseGetters) {
      return fieldName;
    } else {
      final String getterName = LombokUtils.getGetterName(classField);

      final boolean hasGetter;
      @SuppressWarnings("unchecked") final boolean annotatedWith = PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, Data.class, Value.class, Getter.class);
      if (annotatedWith) {
        final PsiAnnotation getterLombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, Getter.class);
        hasGetter = null == getterLombokAnnotation || null != LombokProcessorUtil.getMethodModifier(getterLombokAnnotation);
      } else {
        hasGetter = PsiMethodUtil.hasMethodByName(PsiClassUtil.collectClassMethodsIntern(psiClass), getterName);
      }

      return hasGetter ? getterName + "()" : fieldName;
    }
  }

  private Collection<String> makeSet(@NotNull Collection<String> exclude) {
    if (exclude.isEmpty()) {
      return Collections.emptySet();
    } else {
      return new HashSet<>(exclude);
    }
  }
}
