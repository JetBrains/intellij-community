// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SafeDeleteTestSupport;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.test.IgnoreTests;
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseKt;
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtFunction;
import org.jetbrains.kotlin.psi.KtObjectDeclaration;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtTypeAlias;
import org.jetbrains.kotlin.psi.KtTypeParameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Safe deletes the elements at the carets of a test data file and compares the result with the expected files. A subclass chooses the
 * flow in {@link #performSafeDelete} and {@link #performRefusedSafeDelete}. All the flows read the same test data.
 * <p>
 * The elements: each caret of the first file with a target marks an element. Several carets delete their elements in one request. A
 * {@code *WithJava} test also opens the file with the same name and the other extension, {@code .java} or {@code .kt}.
 * <p>
 * The options: the directive {@code // SAFE_DELETE_OPTIONS: id=value} gives the answer to the question of an option. The {@code id} is
 * {@link ModPlanOption#getId()} of an option from {@link #knownOptions()}, and the {@code value} is {@code true} or {@code false}. Separate
 * more options with commas:
 * <pre>
 * // SAFE_DELETE_OPTIONS: searchInComments=true, kotlinDeleteSuperDeclarations=true
 * </pre>
 * An unknown id fails the test. The common options of {@link com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions} work in
 * the legacy flow and in the ModPlan flows. A search option that the directive does not set is false in all the flows. The answers go to
 * each flow explicitly: the base {@link #performSafeDelete} gives them to the interactive flows through
 * {@link SafeDeleteTestSupport#applyOptions}, and a headless subclass puts them into the options of its request.
 * <p>
 * The other directives:
 * <ul>
 *   <li>{@code // IGNORE_CONFLICTS} writes the deletion also with conflicts.</li>
 *   <li>{@code // REFUSAL: <reason>} expects that the refactoring refuses the deletion with a
 *   {@link com.intellij.refactoring.modplan.ModPlanAnalysisResult.RefusedReason}, such as {@code NOT_APPLICABLE}.</li>
 *   <li>{@code // IGNORE_K2} on the last line disables the test.</li>
 * </ul>
 * <p>
 * The files next to {@code <test>.kt}:
 * <ul>
 *   <li>{@code <test>.kt.after} holds the expected text after the deletion. {@code <test>.kt.fir.after} replaces it if it exists.</li>
 *   <li>{@code <test>.kt.messages} holds the expected conflicts, sorted, one on each line. Without it, a conflict fails the test.</li>
 *   <li>{@code <test>.kt.nonCodeUsages} holds the expected non-code usages of a deletion with no conflict. A search option that is
 *   true makes the file necessary, see {@link SafeDeleteTestSupport#checkNonCodeUsages}.</li>
 *   <li>{@code <test>.txt}, {@code <test>.xml} and {@code <test>.properties} go into the project as non-code files.</li>
 * </ul>
 */
public abstract class AbstractSafeDeleteTest extends KotlinLightCodeInsightFixtureTestCase {
    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance();
    }

    public void doClassTest(@NotNull String path) throws Exception {
        doTest(path, KtClass.class, false);
    }

    public void doClassTestWithJava(@NotNull String path) throws Exception {
        doTest(path, KtClass.class, true);
    }

    public void doJavaClassTest(@NotNull String path) throws Exception {
        doTest(path, PsiClass.class, true);
    }

    public void doJavaTypeParameterTest(@NotNull String path) throws Exception {
        doTest(path, PsiTypeParameter.class, true);
    }

    public void doObjectTest(@NotNull String path) throws Exception {
        doTest(path, KtObjectDeclaration.class, false);
    }

    public void doFunctionTest(@NotNull String path) throws Exception {
        doTest(path, KtFunction.class, false);
    }

    public void doFunctionTestWithJava(@NotNull String path) throws Exception {
        doTest(path, KtFunction.class, true);
    }

    public void doJavaMethodTest(@NotNull String path) throws Exception {
        doTest(path, PsiMethod.class, true);
    }

    public void doJavaParameterTest(@NotNull String path) throws Exception {
        doTest(path, PsiParameter.class, true);
    }

    public void doPropertyTest(@NotNull String path) throws Exception {
        doTest(path, KtProperty.class, false);
    }

    public void doPropertyTestWithJava(@NotNull String path) throws Exception {
        doTest(path, KtProperty.class, true);
    }

    public void doJavaPropertyTest(@NotNull String path) throws Exception {
        doTest(path, PsiMethod.class, true);
    }

    public void doTypeAliasTest(@NotNull String path) throws Exception {
        doTest(path, KtTypeAlias.class, false);
    }

    public void doTypeParameterTest(@NotNull String path) throws Exception {
        doTest(path, KtTypeParameter.class, false);
    }

    public void doTypeParameterTestWithJava(@NotNull String path) throws Exception {
        doTest(path, KtTypeParameter.class, true);
    }

    public void doValueParameterTest(@NotNull String path) throws Exception {
        doTest(path, KtParameter.class, false);
    }

    public void doValueParameterTestWithJava(@NotNull String path) throws Exception {
        doTest(path, KtParameter.class, true);
    }

    /**
     * @return the options that the {@code // SAFE_DELETE_OPTIONS: id=value} directive of the test data may set
     */
    protected @NotNull List<ModPlanOption<?>> knownOptions() {
        return List.of();
    }

    /**
     * Deletes {@code elements} in one request with {@code answers} to the questions. A conflict throws
     * {@link BaseRefactoringProcessor.ConflictsInTestsException} unless {@code ignoreConflicts}. The interactive flows ask nothing in a unit
     * test, so {@link SafeDeleteTestSupport#applyOptions} gives them {@code answers}.
     *
     * @param nonCodeUsagesRecorder gets the non-code usages of a deletion with no conflict
     */
    protected void performSafeDelete(
            @NotNull List<PsiElement> elements,
            @NotNull Map<ModPlanOption<?>, Object> answers,
            boolean ignoreConflicts,
            @NotNull SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsagesRecorder
    ) {
        SafeDeleteTestSupport.applyOptions(answers, nonCodeUsagesRecorder, getTestRootDisposable());
        PsiElement[] elementArray = elements.toArray(PsiElement.EMPTY_ARRAY);
        if (ignoreConflicts) {
            BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(
                    () -> SafeDeleteHandler.invoke(getProject(), elementArray, null, true, null));
        }
        else {
            SafeDeleteHandler.invoke(getProject(), elementArray, null, true, null);
        }
    }

    /**
     * Deletes {@code elements}, which the refactoring refuses with {@code expectedReason}, a
     * {@link com.intellij.refactoring.modplan.ModPlanAnalysisResult.RefusedReason}. The interactive flows report a refusal with an error, which a
     * unit test throws.
     */
    protected void performRefusedSafeDelete(@NotNull List<PsiElement> elements,
                                            @NotNull Map<ModPlanOption<?>, Object> answers,
                                            @NotNull String expectedReason) {
        SafeDeleteTestSupport.applyOptions(answers, getTestRootDisposable());
        try {
            SafeDeleteHandler.invoke(getProject(), elements.toArray(PsiElement.EMPTY_ARRAY), null, true, null);
        }
        catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
            throw new AssertionError("A refusal is expected, not conflicts: " + e.getMessages(), e);
        }
        catch (RuntimeException e) {
            return;
        }
        fail("The refusal " + expectedReason + " is expected");
    }

    private @NotNull Map<ModPlanOption<?>, Object> answersOf(@NotNull String dataFileText) {
        Map<String, ModPlanOption<?>> optionsById = new LinkedHashMap<>();
        for (ModPlanOption<?> option : knownOptions()) {
            optionsById.put(option.getId(), option);
        }
        Map<ModPlanOption<?>, Object> answers = new LinkedHashMap<>();
        for (String rawOption : InTextDirectivesUtils.findListWithPrefixes(dataFileText, "// SAFE_DELETE_OPTIONS:")) {
            List<String> keyAndValue = StringUtil.split(rawOption, "=");
            assertEquals("Bad option " + rawOption, 2, keyAndValue.size());
            String id = keyAndValue.getFirst().trim();
            ModPlanOption<?> option = optionsById.get(id);
            assertNotNull("Bad option " + id + ": the known ones are " + optionsById.keySet(), option);
            answers.put(option, Boolean.parseBoolean(keyAndValue.get(1).trim()));
        }
        return answers;
    }

    private <T extends PsiElement> void doTest(
            @NotNull String path, @NotNull Class<T> elementClass, boolean withJava) throws Exception {
        String[] filePaths;
        if (withJava) {
            filePaths = new String[] {path, path.endsWith(".java") ? path.replace(".java", ".kt") : path.replace(".kt", ".java")};
        } else {
            filePaths = new String[] {path};
        }

        String dataFileText = FileUtil.loadFile(new File(path));
        Map<ModPlanOption<?>, Object> answers = answersOf(dataFileText);
        boolean ignoreConflicts = InTextDirectivesUtils.isDirectiveDefined(dataFileText, "// IGNORE_CONFLICTS");
        String expectedRefusal = InTextDirectivesUtils.findStringWithPrefixes(dataFileText, "// REFUSAL:");
        SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsagesRecorder = new SafeDeleteTestSupport.NonCodeUsageRecorder();

        // A file with the name of the test and the extension of a non-code file holds the non-code usages in such a file.
        String pathWithoutExtension = FileUtil.getNameWithoutExtension(path);
        for (String nonCodeExtension : List.of("txt", "xml", "properties")) {
            String nonCodePath = pathWithoutExtension + "." + nonCodeExtension;
            if (new File(nonCodePath).exists()) {
                myFixture.copyFileToProject(nonCodePath);
            }
        }

        Editor[] editors = new Editor[filePaths.length];
        int i = 0;
        for (String filePath : filePaths) {
            myFixture.configureByFile(filePath);
            editors[i++] = myFixture.getEditor();
        }

        // Each caret of the first file with a target marks an element; several carets delete their elements in one request.
        List<PsiElement> elements = new ArrayList<>();
        for (Editor editor : editors) {
            for (Caret caret : editor.getCaretModel().getAllCarets()) {
                PsiElement elementAtCaret = TargetElementUtil.getInstance().findTargetElement(
                        editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED, caret.getOffset()
                );
                T element = PsiTreeUtil.getParentOfType(elementAtCaret, elementClass, false);
                if (element != null && !elements.contains(element)) {
                    elements.add(element);
                }
            }
            if (!elements.isEmpty()) break;
        }

        assertFalse("Couldn't find element at caret position", elements.isEmpty());

        IgnoreTests.INSTANCE.runTestIfNotDisabledByFileDirective(
                dataFile().toPath(),
                IgnoreTests.DIRECTIVES.IGNORE_K2,
                _ -> Collections.emptyList(),
                IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE,
                _ -> {
                    return KotlinLightCodeInsightFixtureTestCaseKt.withCustomCompilerOptions(myFixture.getFile().getText(), getProject(),
                                                                                             getModule(), () -> {
                                try {
                                    if (expectedRefusal != null) {
                                        performRefusedSafeDelete(elements, answers, expectedRefusal);
                                    }
                                    else {
                                        performSafeDelete(elements, answers, ignoreConflicts, nonCodeUsagesRecorder);
                                    }
                                    if (new File(path + ".messages").exists()) {
                                        throw new AssertionError("Expected conflicts are not found.");
                                    }
                                    if (!ignoreConflicts) {
                                        SafeDeleteTestSupport.checkNonCodeUsages(nonCodeUsagesRecorder, answers, Path.of(path + ".nonCodeUsages"));
                                    }
                                    for (int j = 0; j < filePaths.length; j++) {
                                        File file = new File(filePaths[j] + ".after");
                                        File firSpecific = new File(filePaths[j] + ".fir.after");
                                        if (firSpecific.exists()) {
                                            file = firSpecific;
                                        }
                                        assertTrue("After file is expected to be present", file.exists());
                                        String actualText = editors[j].getDocument().getText();
                                        String expectedText = FileUtil.loadFile(file);
                                        if (!StringUtil.equals(expectedText, actualText)) {
                                            //do not create missed files
                                            String message = "Text mismatch in file: " + file.getName();
                                            throw new FileComparisonFailedError(message, expectedText, actualText,
                                                                                FileUtil.toSystemIndependentName(file.getPath()));
                                        }
                                    }
                                } catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
                                    List<String> messages = new ArrayList<>(e.getMessages());
                                    Collections.sort(messages);

                                    File messageFile = new File(path + ".messages");
                                    assertSameLinesWithFile(messageFile.getAbsolutePath(), StringUtil.join(messages, "\n"));
                                } catch (IOException e) {
                                    throw new AssertionError(e);
                                }
                                return Unit.INSTANCE;
                            });
                }
        );
    }
}
