package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class EqualsAndHashCodeToStringHandler {

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

    public boolean isField() {
      return psiField != null;
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

  public static Collection<MemberInfo> filterMembers(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation,
                                                     boolean filterTransient, String includeAnnotationProperty,
                                                     @Nullable ConfigKey onlyExplicitlyIncludedConfigKey) {
    final boolean explicitOf = PsiAnnotationUtil.hasDeclaredProperty(psiAnnotation, "of");
    final boolean onlyExplicitlyIncluded = checkOnlyExplicitlyIncluded(psiClass, psiAnnotation, onlyExplicitlyIncludedConfigKey);

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
        if (!(psiMember instanceof PsiField psiField)) {
          continue;
        }
        final String fieldName = psiField.getName();

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
        final String includeNameValue = PsiAnnotationUtil.getStringAnnotationValue(includeAnnotation, includeAnnotationProperty, "");
        final String newMemberName;
        if (StringUtil.isEmptyOrSpaces(includeNameValue)) {
          newMemberName = psiMember.getName();
        } else {
          newMemberName = includeNameValue;
        }

        if ((psiMember instanceof PsiMethod psiMethod)) {
          if (!psiMethod.hasParameters()) {
            fieldNames2BeReplaced.add(newMemberName);
            int memberRank = calcMemberRank(includeAnnotation);
            result.add(new MemberInfo(psiMethod, psiMethod.getName(), memberRank));
          }
        } else {
          int memberRank = calcMemberRank(includeAnnotation);
          result.add(new MemberInfo((PsiField)psiMember, newMemberName, memberRank));
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

  private static boolean checkOnlyExplicitlyIncluded(@NotNull PsiClass psiClass,
                                                     @NotNull PsiAnnotation psiAnnotation,
                                                     @Nullable ConfigKey onlyExplicitlyIncludedConfigKey) {
    final boolean onlyExplicitlyIncluded;
    final Boolean declaredAnnotationValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, "onlyExplicitlyIncluded");
    if (null == declaredAnnotationValue) {
      if (null != onlyExplicitlyIncludedConfigKey) {
        onlyExplicitlyIncluded = ConfigDiscovery.getInstance().getBooleanLombokConfigProperty(onlyExplicitlyIncludedConfigKey, psiClass);
      }
      else {
        onlyExplicitlyIncluded = false;
      }
    }
    else {
      onlyExplicitlyIncluded = declaredAnnotationValue;
    }
    return onlyExplicitlyIncluded;
  }

  private static int calcMemberRank(@NotNull PsiAnnotation includeAnnotation) {
    return PsiAnnotationUtil.getIntAnnotationValue(includeAnnotation, TO_STRING_RANK_ANNOTATION_PARAMETER, 0);
  }

  public static String getMemberAccessorName(@NotNull MemberInfo memberInfo, boolean doNotUseGetters, @NotNull PsiClass psiClass) {
    final String memberAccessor;
    if (null == memberInfo.getMethod()) {
      memberAccessor = buildAttributeNameString(doNotUseGetters, memberInfo.getField(), psiClass);
    } else {
      memberAccessor = memberInfo.getName() + "()";
    }
    return memberAccessor;
  }

  private static String buildAttributeNameString(boolean doNotUseGetters, @NotNull PsiField classField, @NotNull PsiClass psiClass) {
    final String fieldName = classField.getName();
    if (doNotUseGetters) {
      return fieldName;
    } else {
      final String getterName = LombokUtils.getGetterName(classField);

      final boolean hasGetter;
      final boolean annotatedWith =
        PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, LombokClassNames.DATA, LombokClassNames.VALUE, LombokClassNames.GETTER);
      if (annotatedWith) {
        final PsiAnnotation getterLombokAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, LombokClassNames.GETTER);
        hasGetter = null == getterLombokAnnotation || null != LombokProcessorUtil.getMethodModifier(getterLombokAnnotation);
      } else {
        hasGetter = PsiMethodUtil.hasMethodByName(PsiClassUtil.collectClassMethodsIntern(psiClass), getterName, 0);
      }

      return hasGetter ? getterName + "()" : fieldName;
    }
  }

  private static Collection<String> makeSet(@NotNull Collection<String> exclude) {
    if (exclude.isEmpty()) {
      return Collections.emptySet();
    } else {
      return new HashSet<>(exclude);
    }
  }
}
