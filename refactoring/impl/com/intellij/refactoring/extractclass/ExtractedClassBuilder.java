package com.intellij.refactoring.extractclass;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.psi.MethodInheritanceUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class ExtractedClassBuilder {
    private static final Logger LOGGER =
            Logger.getInstance("com.siyeh.rpp.extractclass.ExtractedClassBuilder");

    private String className = null;
    private String packageName = null;
    private final List<FieldSpec> fields = new ArrayList<FieldSpec>(5);
    private final List<PsiMethod> methods = new ArrayList<PsiMethod>(5);
    private final List<PsiClassInitializer> initializers = new ArrayList<PsiClassInitializer>(5);
    private final List<PsiClass> innerClasses = new ArrayList<PsiClass>(5);
    private final List<PsiClass> innerClassesToMakePublic = new ArrayList<PsiClass>(5);
    private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
    private final List<PsiClass> interfaces = new ArrayList<PsiClass>();
    private CodeStyleSettings settings = null;
    private boolean requiresBackPointer = false;
    private String originalClassName = null;
    private String backPointerName = null;

    public void setClassName(String className) {
        this.className = className;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setOriginalClassName(String originalClassName) {
        this.originalClassName = originalClassName;
    }

    public void addField(PsiField field, boolean getterRequired, boolean setterRequired) {
        final FieldSpec fieldSpec = new FieldSpec(field, getterRequired, setterRequired);
        fields.add(fieldSpec);
    }

    public void addMethod(PsiMethod method) {
        methods.add(method);
    }

    public void addInitializer(PsiClassInitializer initializer) {
        initializers.add(initializer);
    }

    public void addInnerClass(PsiClass innerClass, boolean makePublic) {
        innerClasses.add(innerClass);
        if (makePublic) {
            innerClassesToMakePublic.add(innerClass);
        }
    }

    public void setTypeArguments(List<PsiTypeParameter> typeParams) {
        this.typeParams.clear();
        this.typeParams.addAll(typeParams);
    }

    public void setInterfaces(List<PsiClass> interfaces) {
        this.interfaces.clear();
        this.interfaces.addAll(interfaces);
    }

    public void setCodeStyleSettings(CodeStyleSettings settings) {
        this.settings = settings;
    }

    public String buildBeanClass() throws IOException {
        if (requiresBackPointer) {
            calculateBackpointerName();
        }
        @NonNls final StringBuffer out = new StringBuffer(1024);
        out.append("package " + packageName + ';');

        out.append('\n');
        out.append("public ");
        if (hasAbstractMethod()) {
            out.append("abstract ");
        }
        out.append("class ");
        out.append(className);
        if (!typeParams.isEmpty()) {
            out.append('<');
            boolean first = true;
            for (PsiTypeParameter typeParam : typeParams) {
                if (!first) {
                    out.append(',');
                }
                out.append(typeParam.getText());
                first = false;
            }
            out.append('>');
        }
        out.append('\n');
        if (!interfaces.isEmpty()) {
            out.append("implements ");
            boolean first = true;
            for (PsiClass implemented : interfaces) {
                if (!first) {
                    out.append(',');
                }
                out.append(implemented.getQualifiedName());
                first = false;
            }
        }
        out.append('{');

        if (requiresBackPointer) {
            out.append("private final " + originalClassName);
            if (!typeParams.isEmpty()) {
                out.append('<');
                boolean first = true;
                for (PsiTypeParameter typeParam : typeParams) {
                    if (!first) {
                        out.append(',');
                    }
                    out.append(typeParam.getName());
                    first = false;
                }
                out.append('>');
            }
            out.append(' ' + backPointerName + ";\n");
        }
        outputFieldsAndInitializers(out);
        out.append('\n');
        if (hasNonStatic() || requiresBackPointer) {
            outputConstructor(out);
            out.append('\n');
        }
        outputMethods(out);
        outputInnerClasses(out);
        outputGetters(out);
        outputSetters(out);
        out.append("}\n");
        return out.toString();
    }

    private boolean hasAbstractMethod() {
        for (PsiMethod method : methods) {
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return true;
            }
        }
        return false;
    }

    private void calculateBackpointerName() {
        final String baseName;
        if (originalClassName.indexOf((int) '.') == 0) {
            baseName = toLowerCase(originalClassName);
        } else {
            final String simpleClassName = originalClassName.substring(originalClassName.lastIndexOf('.') + 1);
            baseName = toLowerCase(simpleClassName);
        }
        String name = settings.FIELD_NAME_PREFIX + baseName + settings.FIELD_NAME_SUFFIX;
        if (!existsFieldWithName(name)) {
            backPointerName = name;
            return;
        }
        int counter = 1;
        while (true) {
            name = settings.FIELD_NAME_PREFIX + baseName + counter + settings.FIELD_NAME_SUFFIX;
            if (!existsFieldWithName(name)) {
                backPointerName = name;
                return;
            }
            counter++;
        }
    }

    private static String toLowerCase(String name) {
        if (name.length() > 0) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        } else {
            return String.valueOf(Character.toLowerCase(name.charAt(0)));
        }
    }

    private boolean existsFieldWithName(String name) {
        for (FieldSpec fieldSpec : fields) {
            final PsiVariable field = fieldSpec.getField();
            final String fieldName = field.getName();
            if (name.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonStatic() {
        for (FieldSpec fieldSpec : fields) {
            final PsiVariable field = fieldSpec.getField();
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        for (PsiMethod method : methods) {
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
        }
        return false;
    }

    private void outputMethods(StringBuffer out) {
        for (PsiMethod method : methods) {
            outputMutatedMethod(out, method);
            out.append('\n');
        }
    }

    private void outputInnerClasses(StringBuffer out) {
        for (PsiClass innerClass : innerClasses) {
            outputMutatedInnerClass(out, innerClass, innerClassesToMakePublic.contains(innerClass));
            out.append('\n');
        }
    }

    private void outputMutatedInitializer(StringBuffer out, PsiClassInitializer initializer) {
        final PsiElementVisitor visitor = new Mutator(out);
        initializer.accept(visitor);
    }

    private void outputMutatedMethod(StringBuffer out, PsiMethod method) {
        final PsiElementVisitor visitor = new Mutator(out);
        method.accept(visitor);
    }

    private void outputMutatedInnerClass(StringBuffer out, PsiClass innerClass, boolean makePublic) {
        if (makePublic) {
            try {
                innerClass.getModifierList().setModifierProperty(PsiModifier.PUBLIC, false);
            } catch (IncorrectOperationException e) {
                LOGGER.error(e);
            }
        }
        final PsiElementVisitor visitor = new Mutator(out);
        innerClass.accept(visitor);
    }

    private void outputSetters(StringBuffer out) {
        for (final FieldSpec field : fields) {
            outputSetter(field, out);
        }
    }

    private void outputGetters(StringBuffer out) {
        for (final FieldSpec field : fields) {
            outputGetter(field, out);
        }
    }

    private void outputFieldsAndInitializers(StringBuffer out) {
        final List<PsiClassInitializer> remainingInitializers = new ArrayList<PsiClassInitializer>(initializers);
        for (final FieldSpec field : fields) {
            final PsiVariable psiField = field.getField();
            final Iterator<PsiClassInitializer> initializersIterator = remainingInitializers.iterator();
            final int fieldOffset = psiField.getTextRange().getStartOffset();
            while (initializersIterator.hasNext()) {
                final PsiClassInitializer initializer = initializersIterator.next();
                if (initializer.getTextRange().getStartOffset() < fieldOffset) {
                    outputMutatedInitializer(out, initializer);
                    out.append('\n');
                    initializersIterator.remove();
                }
            }

            outputField(field, out);
        }
        for (PsiClassInitializer initializer : remainingInitializers) {
            outputMutatedInitializer(out, initializer);
            out.append('\n');
        }
    }

    private void outputSetter(FieldSpec field, @NonNls StringBuffer out) {
        if (!field.isSetterRequired()) {
            return;
        }
        final PsiField variable = (PsiField) field.getField();
        final String name = calculateStrippedName(variable);
        final String setterName = createSetterNameForField(variable);
        for (PsiMethod method : methods) {
            if (method.getName().equals(setterName) &&
                    method.getParameterList().getParameters().length == 1) {
                return;
            }
        }
        final String parameterName =
                settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
        final PsiType type = variable.getType();
        final String typeText = type.getCanonicalText();
        out.append("\tpublic ");
        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
            out.append("static ");
        }
        out.append("void ");
        out.append(setterName);
        out.append('(');
        out.append(typeText);
        out.append(' ');
        out.append(parameterName);
        out.append(")\n");
        out.append("\t{\n");

        final String fieldName;
        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
            fieldName = settings.STATIC_FIELD_NAME_PREFIX + name + settings.STATIC_FIELD_NAME_SUFFIX;
        } else {
            fieldName = settings.FIELD_NAME_PREFIX + name + settings.FIELD_NAME_SUFFIX;
        }
        if (fieldName.equals(parameterName)) {
            out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
        } else {
            out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
        }
        out.append("\t}\n");
        out.append('\n');
    }

    private String calculateStrippedName(PsiVariable variable) {
        String name = variable.getName();
        if (name == null) {
            return null;
        }
        if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.STATIC)) {
            if (name.startsWith(settings.STATIC_FIELD_NAME_PREFIX)) {
                name = name.substring(settings.STATIC_FIELD_NAME_PREFIX.length());
            }
            if (name.endsWith(settings.STATIC_FIELD_NAME_SUFFIX)) {
                name = name.substring(0, name.length() - settings.STATIC_FIELD_NAME_SUFFIX.length());
            }
        } else if (variable instanceof PsiField && !variable.hasModifierProperty(PsiModifier.STATIC)) {
            if (name.startsWith(settings.FIELD_NAME_PREFIX)) {
                name = name.substring(settings.FIELD_NAME_PREFIX.length());
            }
            if (name.endsWith(settings.FIELD_NAME_SUFFIX)) {
                name = name.substring(0, name.length() - settings.FIELD_NAME_SUFFIX.length());
            }
        }
        return name;
    }

    private void outputGetter(FieldSpec field, @NonNls StringBuffer out) {
        if (!field.isGetterRequired()) {
            return;
        }

        final PsiVariable variable = field.getField();
        final PsiType type = variable.getType();
        final String typeText = type.getCanonicalText();
        final String name = calculateStrippedName(variable);

        final String capitalizedName = StringUtil.capitalize(name);
        @NonNls final String getterName;
        if (PsiType.BOOLEAN.equals(type)) {
            getterName = "is" + capitalizedName;

        } else {
            getterName = "get" + capitalizedName;
        }
        for (PsiMethod method : methods) {
            if (method.getParameterList().getParameters().length == 0
                    && getterName.equals(method.getName())) {
                return;
            }
        }
        out.append("\tpublic ");
        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
            out.append("static ");
        }
        out.append(typeText);
        out.append(' ');
        out.append(getterName);
        out.append("()\n");
        out.append("\t{\n");
        final String fieldName;
        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
            fieldName = settings.STATIC_FIELD_NAME_PREFIX + name + settings.STATIC_FIELD_NAME_SUFFIX;
        } else {
            fieldName = settings.FIELD_NAME_PREFIX + name + settings.FIELD_NAME_SUFFIX;
        }
        out.append("\t\treturn " + fieldName + ";\n");
        out.append("\t}\n");
        out.append('\n');
    }

    private void outputConstructor(@NonNls StringBuffer out) {
        out.append("\tpublic " + className + '(');
        boolean isFirst = true;
        if (requiresBackPointer) {
            final String parameterName =
                    settings.PARAMETER_NAME_PREFIX + backPointerName + settings.PARAMETER_NAME_SUFFIX;
            out.append(originalClassName);
            if (!typeParams.isEmpty()) {
                out.append('<');
                boolean first = true;
                for (PsiTypeParameter typeParam : typeParams) {
                    if (!first) {
                        out.append(',');
                    }
                    out.append(typeParam.getName());
                    first = false;
                }
                out.append('>');
            }
            out.append(' ' + parameterName);
            isFirst = false;
        }
        for (final FieldSpec field : fields) {
            final PsiVariable variable = field.getField();
            if (variable.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (!variable.hasInitializer()) {
                continue;
            }
            final PsiExpression initializer = variable.getInitializer();
            if (PsiUtil.isConstantExpression(initializer)) {
                continue;
            }
            if (!isFirst) {
                out.append(", ");
            }
            isFirst = false;
            final PsiType type = variable.getType();
            final String typeText = type.getCanonicalText();
            final String name = calculateStrippedName(variable);
            final String parameterName =
                    settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
            out.append(typeText + ' ' + parameterName);
        }

        out.append(")\n");
        out.append("\t{\n");
        if (requiresBackPointer) {
            final String parameterName =
                    settings.PARAMETER_NAME_PREFIX + backPointerName + settings.PARAMETER_NAME_SUFFIX;
            if (backPointerName.equals(parameterName)) {
                out.append("\t\tthis." + backPointerName + " = " + parameterName + ";\n");
            } else {
                out.append("\t\t" + backPointerName + " = " + parameterName + ";\n");
            }

        }
        for (final FieldSpec field : fields) {
            final PsiVariable variable = field.getField();
            if (variable.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (!variable.hasInitializer()) {
                continue;
            }
            final PsiExpression initializer = variable.getInitializer();
            if (PsiUtil.isConstantExpression(initializer)) {
                continue;
            }
            final String name = calculateStrippedName(variable);
            final String fieldName;
            if (variable.hasModifierProperty(PsiModifier.STATIC)) {
                fieldName = settings.STATIC_FIELD_NAME_PREFIX + name + settings.STATIC_FIELD_NAME_SUFFIX;
            } else {
                fieldName = settings.FIELD_NAME_PREFIX + name + settings.FIELD_NAME_SUFFIX;
            }
            final String parameterName =
                    settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
            if (fieldName.equals(parameterName)) {
                out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
            } else {
                out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
            }
        }
        out.append("\t}\n");
        out.append('\n');
    }

    private void outputField(FieldSpec field, @NonNls StringBuffer out) {
        final PsiVariable variable = field.getField();
        final PsiDocComment docComment = getJavadocForVariable(variable);
        if (docComment != null) {
            out.append(docComment.getText());
            out.append('\n');
        }
        final PsiType type = variable.getType();
        final String typeText = type.getCanonicalText();
        final String name = calculateStrippedName(variable);

        @NonNls String modifierString;
        if (variable.hasModifierProperty(PsiModifier.PUBLIC) &&
                variable.hasModifierProperty(PsiModifier.STATIC)) {
            modifierString = "public ";
        } else {
            modifierString = "private ";
        }
        final String fieldName;
        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
            modifierString += "static ";
            fieldName = settings.STATIC_FIELD_NAME_PREFIX + name + settings.STATIC_FIELD_NAME_SUFFIX;
        } else {
            fieldName = settings.FIELD_NAME_PREFIX + name + settings.FIELD_NAME_SUFFIX;
        }
        if (!field.isSetterRequired() &&
                variable.hasModifierProperty(PsiModifier.FINAL)) {
            modifierString += "final ";
        }
        if (variable.hasModifierProperty(PsiModifier.TRANSIENT)) {
            modifierString += "transient ";
        }
        final PsiModifierList modifierList = variable.getModifierList();
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            final String annotationText = annotation.getText();
            out.append(annotationText);
        }
        out.append('\t');
        out.append(modifierString);
        out.append(typeText);
        out.append(' ');
        out.append(fieldName);
        if (variable.hasInitializer()) {
            final PsiExpression initializer = variable.getInitializer();
            if (PsiUtil.isConstantExpression(initializer)) {
                out.append('=');
                out.append(initializer.getText());
            }
        }
        out.append(";\n");
    }

    private static PsiDocComment getJavadocForVariable(PsiVariable variable) {
        final PsiElement[] children = variable.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiDocComment) {
                return (PsiDocComment) child;
            }
        }
        return null;
    }

    public void setRequiresBackPointer(boolean requiresBackPointer) {
        this.requiresBackPointer = requiresBackPointer;
    }


    @NonNls
    private String createSetterNameForField(PsiField field) {

        final String name = calculateStrippedName(field);
        final String capitalizedName = StringUtil.capitalize(name);
        return "set" + capitalizedName;
    }

    @NonNls
    private String createGetterNameForField(PsiField field) {
        final String name = calculateStrippedName(field);
        final String capitalizedName = StringUtil.capitalize(name);
        if (PsiType.BOOLEAN.equals(field.getType())) {
            return "is" + capitalizedName;
        } else {
            return "get" + capitalizedName;
        }
    }

    private class Mutator extends PsiElementVisitor {
        @NonNls
        private final StringBuffer out;

        private Mutator(StringBuffer out) {
            super();
            this.out = out;
        }

        public void visitElement(PsiElement element) {

            super.visitElement(element);
            final PsiElement[] children = element.getChildren();
            if (children.length == 0) {
                final String text = element.getText();
                out.append(text);
            } else {
                for (PsiElement child : children) {
                    child.accept(this);
                }
            }
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {

            final PsiElement qualifier = expression.getQualifier();
            if (qualifier == null || qualifier instanceof PsiThisExpression) {
                final PsiElement referent = expression.resolve();
                if (referent instanceof PsiField) {
                    final PsiField field = (PsiField) referent;

                    if (fieldIsExtracted(field)) {

                        final String name = calculateStrippedName(field);
                        final String fieldName;
                        if (field.hasModifierProperty(PsiModifier.STATIC)) {
                            fieldName = settings.STATIC_FIELD_NAME_PREFIX + name + settings.STATIC_FIELD_NAME_SUFFIX;
                        } else {
                            fieldName = settings.FIELD_NAME_PREFIX + name + settings.FIELD_NAME_SUFFIX;
                        }
                        if (qualifier != null && fieldName.equals(expression.getReferenceName())) {
                            out.append("this.");
                        }
                        out.append(fieldName);
                    } else {
                        if (field.hasModifierProperty(PsiModifier.STATIC)) {
                            if (field.hasModifierProperty(PsiModifier.PUBLIC)) {
                                out.append(originalClassName + '.' + field.getName());
                            } else {
                                out.append(originalClassName + '.' + createGetterNameForField(field) + "()");
                            }
                        } else {
                            out.append(backPointerName + '.' + createGetterNameForField(field) + "()");
                        }
                    }
                } else {
                    visitElement(expression);
                }
            } else {
                visitElement(expression);
            }
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            PsiExpression lhs = expression.getLExpression();
            final PsiExpression rhs = expression.getRExpression();

            if (isBackpointerReference(lhs) && rhs != null) {
                while (lhs instanceof PsiParenthesizedExpression) {
                    lhs = ((PsiParenthesizedExpression) lhs).getExpression();
                }

                final PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
                assert reference != null;
                final PsiField field = (PsiField) reference.resolve();
                final PsiJavaToken sign = expression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                assert field != null;
                if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                    if (tokenType.equals(JavaTokenType.EQ)) {
                        final String setterName = createSetterNameForField(field);
                        out.append(backPointerName + '.' + setterName + '(');
                        rhs.accept(this);
                        out.append(')');
                    } else {
                        final String operator = sign.getText().substring(0, sign.getTextLength() - 1);
                        final String setterName = createSetterNameForField(field);
                        out.append(backPointerName + '.' + setterName + '(');
                        final String getterName = createGetterNameForField(field);
                        out.append(backPointerName + '.' + getterName + "()");
                        out.append(operator);
                        rhs.accept(this);
                        out.append(')');
                    }
                } else if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
                    if (tokenType.equals(JavaTokenType.EQ)) {
                        final String setterName = createSetterNameForField(field);
                        out.append(originalClassName + '.' + setterName + '(');
                        rhs.accept(this);
                        out.append(')');
                    } else {
                        final String operator = sign.getText().substring(0, sign.getTextLength() - 1);
                        final String setterName = createSetterNameForField(field);
                        out.append(originalClassName + '.' + setterName + '(');
                        final String getterName = createGetterNameForField(field);
                        out.append(originalClassName + '.' + getterName + "()");
                        out.append(operator);
                        rhs.accept(this);
                        out.append(')');
                    }
                } else {
                    visitElement(expression);  //for public static fields, the
                }
            } else {
                visitElement(expression);
            }
        }


        public void visitPostfixExpression(PsiPostfixExpression expression) {
            PsiExpression operand = expression.getOperand();
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (isBackpointerReference(operand) &&
                    (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS))) {
                while (operand instanceof PsiParenthesizedExpression) {
                    operand = ((PsiParenthesizedExpression) operand).getExpression();
                }
                final PsiReferenceExpression reference = (PsiReferenceExpression) operand;

                final String operator;
                if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
                    operator = "+";
                } else {
                    operator = "-";
                }
                final PsiField field = (PsiField) reference.resolve();
                if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                    out.append(backPointerName + '.' + createSetterNameForField(field) +
                            '(' + backPointerName + '.' + createGetterNameForField(field) + "()" + operator + "1)");
                } else if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
                    out.append(originalClassName + '.' + createSetterNameForField(field) +
                            '(' + originalClassName + '.' + createGetterNameForField(field) + "()" + operator + "1)");
                } else {
                    visitElement(expression);
                }
            } else {
                visitElement(expression);
            }
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            PsiExpression operand = expression.getOperand();
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (isBackpointerReference(operand) &&
                    (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS))) {
                while (operand instanceof PsiParenthesizedExpression) {
                    operand = ((PsiParenthesizedExpression) operand).getExpression();
                }
                final PsiReferenceExpression reference = (PsiReferenceExpression) operand;

                final String operator;
                if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
                    operator = "+";
                } else {
                    operator = "-";
                }
                final PsiField field = (PsiField) reference.resolve();
                assert field != null;
                if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                    out.append(backPointerName + '.' + createSetterNameForField(field) +
                            '(' + backPointerName + '.' + createGetterNameForField(field) + "()" + operator + "1)");
                } else if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
                    out.append(originalClassName + '.' + createSetterNameForField(field) +
                            '(' + originalClassName + '.' + createGetterNameForField(field) + "()" + operator + "1)");
                } else {
                    visitElement(expression);
                }
            } else {
                visitElement(expression);
            }
        }

        private boolean isBackpointerReference(PsiExpression expression) {
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression contents = ((PsiParenthesizedExpression) expression).getExpression();
                return isBackpointerReference(contents);
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression reference = (PsiReferenceExpression) expression;
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
                return false;
            }
            final PsiElement referent = reference.resolve();
            return referent instanceof PsiField && !fieldIsExtracted((PsiField) referent);
        }


        public void visitThisExpression(PsiThisExpression expression) {
            out.append(backPointerName);
        }

        private boolean fieldIsExtracted(PsiField field) {
            for (FieldSpec fieldSpec : fields) {
                if (fieldSpec.getField().equals(field)) {
                    return true;
                }
            }
            final PsiClass containingClass = field.getContainingClass();
            return innerClasses.contains(containingClass);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            final PsiReferenceExpression expression = call.getMethodExpression();
            final PsiElement qualifier = expression.getQualifier();
            if (qualifier == null || qualifier instanceof PsiThisExpression) {
                final PsiMethod method = call.resolveMethod();
                if (method != null && !isCompletelyMoved(method)) {
                    final String methodName = method.getName();
                    if (method.hasModifierProperty(PsiModifier.STATIC)) {
                        out.append(originalClassName + '.' + methodName);
                    } else {
                        out.append(backPointerName + '.' + methodName);
                    }
                    final PsiExpressionList argumentList = call.getArgumentList();
                    argumentList.accept(this);
                } else {
                    visitElement(call);
                }
            } else {
                visitElement(call);
            }
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            final String referenceText = reference.getCanonicalText();
            out.append(referenceText);
        }

        private boolean isCompletelyMoved(PsiMethod method) {
            return methods.contains(method) && !
                    MethodInheritanceUtils.hasSiblingMethods(method);
        }
    }
}

