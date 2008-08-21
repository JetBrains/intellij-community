package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ReturnValueBeanBuilder {
    private String className = null;
    private String packageName = null;
    private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
    private CodeStyleSettings settings = null;
    private PsiType valueType = null;

    public void setClassName(String className) {
        this.className = className;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setTypeArguments(List<PsiTypeParameter> typeParams) {
        this.typeParams.clear();
        this.typeParams.addAll(typeParams);
    }

    public void setCodeStyleSettings(CodeStyleSettings settings) {
        this.settings = settings;
    }

    public String buildBeanClass() throws IOException {
        @NonNls final StringBuffer out = new StringBuffer(1024);
       
        out.append("package " + packageName + ';');
        out.append('\n');
        out.append("public class " + className);
        if (!typeParams.isEmpty()) {
            out.append('<');
            boolean first = true;
            for (PsiTypeParameter typeParam : typeParams) {
                if (!first) {
                    out.append(',');
                }
                final String parameterText = typeParam.getText();
                out.append(parameterText);
                first = false;
            }
            out.append('>');
        }
        out.append('\n');

        out.append('{');
        outputField(out);
        out.append('\n');
        outputConstructor(out);
        out.append('\n');
        outputGetter(out);
        out.append("}\n");
        return out.toString();
    }

    private void outputGetter(@NonNls StringBuffer out) {
        final String typeText = valueType.getCanonicalText();
        @NonNls final String name = "value";
        final String capitalizedName = StringUtil.capitalize(name);
        out.append("\tpublic " + typeText + " get" + capitalizedName + "()\n");
        out.append("\t{\n");
        final String prefix = settings.FIELD_NAME_PREFIX;
        final String suffix = settings.FIELD_NAME_SUFFIX;
        final String fieldName = prefix + name + suffix;
        out.append("\t\treturn " + fieldName + ";\n");
        out.append("\t}\n");
        out.append('\n');
    }

    private void outputField(@NonNls StringBuffer out) {
        final String typeText = valueType.getCanonicalText();
        @NonNls final String name = "value";
        final String prefix = settings.FIELD_NAME_PREFIX;
        final String suffix = settings.FIELD_NAME_SUFFIX;
        out.append('\t' + "private final " + typeText + ' ' + prefix + name + suffix + ";\n");
    }

    private void outputConstructor(@NonNls StringBuffer out) {
        out.append("\tpublic " + className + '(');
        final String typeText = valueType.getCanonicalText();
        @NonNls final String name = "value";
        final String parameterName =
                settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
        out.append(settings.GENERATE_FINAL_PARAMETERS ? "final " : "");
        out.append(typeText + ' ' + parameterName);
        out.append(")\n");
        out.append("\t{\n");
        final String fieldName = settings.FIELD_NAME_PREFIX + name + settings.FIELD_NAME_SUFFIX;
        if (fieldName.equals(parameterName)) {
            out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
        } else {
            out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
        }
        out.append("\t}\n");
        out.append('\n');
    }

    public void setValueType(PsiType valueType) {
        this.valueType = valueType;
    }
}

