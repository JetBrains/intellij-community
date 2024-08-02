// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.actions.CreateTemplateInPackageAction
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinStatus
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.statistics.KotlinCreateFileFUSCollector
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.KotlinParserDefinition.Companion.STD_SCRIPT_SUFFIX
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.util.concurrent.Callable
import javax.swing.Icon

internal class NewKotlinFileAction : AbstractNewKotlinFileAction(), DumbAware {
    override fun isAvailable(dataContext: DataContext): Boolean {
        if (!super.isAvailable(dataContext)) return false

        val ideView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex

        return ideView.directories.any {
            val virtualFile = it.virtualFile
            RootKindFilter.projectSources.matches(project, virtualFile) ||
                    CreateTemplateInPackageAction.isInContentRoot(virtualFile, projectFileIndex)
        }
    }

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        KotlinJ2KOnboardingFUSCollector.logKtFileDialogOpened(project)
        val sealedTemplatesEnabled = RegistryManager.getInstance().`is`("kotlin.create.sealed.templates.enabled")

        builder.setTitle(KotlinBundle.message("action.new.file.dialog.title"))

        builder
            .addKind(KotlinFileTemplate.Class)
            .addKind(KotlinFileTemplate.File)
            .addKind(KotlinFileTemplate.Interface)

        if (sealedTemplatesEnabled && project.languageVersionSettings.supportsFeature(LanguageFeature.SealedInterfaces)) {
            builder.addKind(KotlinFileTemplate.SealedInterface)
        }

        builder
            .addKind(KotlinFileTemplate.DataClass)
            .addKind(KotlinFileTemplate.Enum)

        if (sealedTemplatesEnabled) {
            builder.addKind(KotlinFileTemplate.SealedClass)
        }

        builder
            .addKind(KotlinFileTemplate.Annotation)
            .addKind(KotlinFileTemplate.Script)
            .addKind(KotlinFileTemplate.Object)

        builder.setValidator(NewKotlinFileNameValidator)
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        KotlinBundle.message("action.Kotlin.NewFile.text")

    override fun hashCode(): Int = 0

    override fun equals(other: Any?): Boolean = other is NewKotlinFileAction
}

internal abstract class AbstractNewKotlinFileAction : CreateFileFromTemplateAction() {

    private fun KtFile.editor(): Editor? =
        FileEditorManager.getInstance(this.project).selectedTextEditor?.takeIf { it.document == this.viewProvider.document }

    override fun postProcess(createdElement: PsiFile, templateName: String?, customProperties: Map<String, String>?) {
        super.postProcess(createdElement, templateName, customProperties)

        val module = ModuleUtilCore.findModuleForPsiElement(createdElement)

        if (createdElement is KtFile) {
            if (module != null) {
                for (hook in NewKotlinFileHook.EP_NAME.extensions) {
                    hook.postProcess(createdElement, module)
                }
            }

            val ktClass = createdElement.declarations.singleOrNull() as? KtNamedDeclaration
            if (ktClass != null) {
                if (ktClass is KtClass && ktClass.isData()) {
                    val primaryConstructor = ktClass.primaryConstructor
                    if (primaryConstructor != null) {
                        createdElement.editor()?.caretModel?.moveToOffset(primaryConstructor.startOffset + 1)
                        return
                    }
                }
                moveCaretAfterNameIdentifier(ktClass)
            } else {
                val editor = createdElement.editor() ?: return
                val lineCount = editor.document.lineCount
                if (lineCount > 0) {
                    editor.caretModel.moveToLogicalPosition(LogicalPosition(lineCount - 1, 0))
                }
            }
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? =
        createFileFromTemplateWithStat(name, template, dir)
}

@ApiStatus.Internal
object NewKotlinFileNameValidator : InputValidatorEx {
    override fun getErrorText(inputString: String): String? {
        if (inputString.trim().isEmpty()) {
            return KotlinBundle.message("action.new.file.error.empty.name")
        }

        val parts: List<String> = inputString.split(*FQNAME_SEPARATORS)
        if (parts.any { it.trim().isEmpty() }) {
            return KotlinBundle.message("action.new.file.error.empty.name.part")
        }

        return null
    }

    override fun checkInput(inputString: String): Boolean = true

    override fun canClose(inputString: String): Boolean = getErrorText(inputString) == null
}

private fun findOrCreateTarget(dir: PsiDirectory, name: String, directorySeparators: CharArray): Pair<String, PsiDirectory> {
    var fileName = removeKotlinExtensionIfPresent(name)
    var targetDir = dir

    for (splitChar in directorySeparators) {
        if (splitChar in fileName) {
            val names = fileName.trim().split(splitChar)

            var fileNameParts = 1
            if (splitChar == '.') {
                val classNameIndex = names
                    .cutExistentPath(targetDir)
                    .reversed()
                    .indexOfFirst { it.isNotBlank() && Character.isUpperCase(it.first()) }
                if (classNameIndex != -1) {
                    fileNameParts = classNameIndex + 1
                }
            }

            for (dirName in names.dropLast(fileNameParts)) {
                targetDir = targetDir.findSubdirectory(dirName) ?: runWriteAction {
                    targetDir.createSubdirectory(dirName)
                }
            }

            fileName = names.takeLast(fileNameParts).joinToString(".")
            break
        }
    }
    return Pair(fileName, targetDir)
}

private fun List<String>.cutExistentPath(targetDir: PsiDirectory): List<String> {
    var i = 0
    var dir = targetDir
    for (name in this) {
        dir = dir.findSubdirectory(name) ?: break
        i++
    }
    return takeLast(size - i)
}

const val KOTLIN_WORKSHEET_EXTENSION: String = "ws.kts"

private fun removeKotlinExtensionIfPresent(name: String): String = when {
    name.endsWith(".$KOTLIN_WORKSHEET_EXTENSION") -> name.removeSuffix(".$KOTLIN_WORKSHEET_EXTENSION")
    name.endsWith(".$STD_SCRIPT_SUFFIX") -> name.removeSuffix(".$STD_SCRIPT_SUFFIX")
    name.endsWith(".${KotlinFileType.EXTENSION}") -> name.removeSuffix(".${KotlinFileType.EXTENSION}")
    else -> name
}

private fun createKotlinFileFromTemplate(dir: PsiDirectory, fileName: String, template: FileTemplate): PsiFile? {
    val project = dir.project
    val className = fileName.substringBefore('.')
    val attributesDefaults = AttributesDefaults(className).withFixedName(true)
    val defaultProperties = FileTemplateManager.getInstance(project).defaultProperties.apply {
        put(FileTemplate.ATTRIBUTE_FILE_NAME, fileName)
    }
    val element = try {
        CreateFromTemplateDialog(
            project, dir, template,
            attributesDefaults,
            defaultProperties
        ).create()
    } catch (e: IncorrectOperationException) {
        throw e
    } catch (e: Exception) {
        logger<NewKotlinFileAction>().error(e)
        return null
    }

    return element?.containingFile
}

private val FILE_SEPARATORS: CharArray = charArrayOf('/', '\\')
private val FQNAME_SEPARATORS: CharArray = charArrayOf('/', '\\', '.')

internal fun createFileFromTemplateWithStat(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
    KotlinJ2KOnboardingFUSCollector.logFirstKtFileCreated(dir.project) // implementation checks if it is actually the first
    KotlinCreateFileFUSCollector.logFileTemplate(template.name)
    return createKotlinFileFromTemplate(name, template, dir)
}

fun createKotlinFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
    val directorySeparators = when (template.name) {
        "Kotlin File" -> FILE_SEPARATORS
        "Kotlin Worksheet" -> FILE_SEPARATORS
        "Kotlin Script" -> FILE_SEPARATORS
        else -> FQNAME_SEPARATORS
    }

    val (fileName, targetDir) = findOrCreateTarget(dir, name, directorySeparators)

    val service = DumbService.getInstance(dir.project)
    return service.computeWithAlternativeResolveEnabled<PsiFile?, Throwable> {
        val adjustedDir = CreateTemplateInPackageAction.adjustDirectory(targetDir, JavaModuleSourceRootTypes.SOURCES)
        val psiFile = createKotlinFileFromTemplate(adjustedDir, fileName, template)
        if (psiFile is KtFile) {
            val singleClass = psiFile.declarations.singleOrNull() as? KtClass
            if (singleClass != null && !singleClass.isEnum() && !singleClass.isInterface() && name.contains("Abstract")) {
                runWriteAction {
                    singleClass.addModifier(KtTokens.ABSTRACT_KEYWORD)
                }
            }
        }
        JavaCreateTemplateInPackageAction.setupJdk(adjustedDir, psiFile)
        val module = ModuleUtil.findModuleForFile(psiFile) ?: return@computeWithAlternativeResolveEnabled psiFile

        // Old JPS configurator logic
        // TODO: Unify with other auto-configuration logic in NewKotlinFileConfigurationHook
        val configurator = KotlinProjectConfigurator.EP_NAME.extensions.firstOrNull {
            // Gradle is already covered by the auto-configuration feature in NewKotlinFileConfigurationHook
            it.isApplicable(module) && module.buildSystemType != BuildSystemType.Gradle
        }
        if (configurator != null) {
            ReadAction.nonBlocking(Callable {
                configurator.getStatus(module.toModuleGroup()) == ConfigureKotlinStatus.CAN_BE_CONFIGURED
            })
                .inSmartMode(module.project)
                .finishOnUiThread(ModalityState.nonModal()) {
                    if (module.project.isDisposed) {
                        return@finishOnUiThread
                    }
                    if (it) {
                        configurator.configure(module.project, emptyList())
                    }
                }.submit(AppExecutorUtil.getAppExecutorService())
        }
        psiFile
    }
}

internal fun CreateFileFromTemplateDialog.Builder.addKind(t: KotlinFileTemplate) =
    addKind(t.title, t.icon, t.fileName)

internal enum class KotlinFileTemplate(@NlsContexts.ListItem val title: String, val icon: Icon, val fileName: String) {
    Class(KotlinBundle.message("action.new.file.dialog.class.title"), KotlinIcons.CLASS, "Kotlin Class"),
    File(
        KotlinBundle.message("action.new.file.dialog.file.title"),
        Cancellation.forceNonCancellableSectionInClassInitializer { KotlinFileType.INSTANCE.icon },
        "Kotlin File"
    ),
    Interface(KotlinBundle.message("action.new.file.dialog.interface.title"), KotlinIcons.INTERFACE, "Kotlin Interface"),
    SealedInterface(KotlinBundle.message("action.new.file.dialog.sealed.interface.title"), KotlinIcons.INTERFACE, "Kotlin Sealed Interface"),
    DataClass(KotlinBundle.message("action.new.file.dialog.data.class.title"), KotlinIcons.CLASS, "Kotlin Data Class"),
    Enum(KotlinBundle.message("action.new.file.dialog.enum.title"), KotlinIcons.ENUM, "Kotlin Enum"),
    SealedClass(KotlinBundle.message("action.new.file.dialog.sealed.class.title"), KotlinIcons.CLASS, "Kotlin Sealed Class"),
    Annotation(KotlinBundle.message("action.new.file.dialog.annotation.title"), KotlinIcons.ANNOTATION, "Kotlin Annotation"),
    Script(KotlinBundle.message("action.new.script.name"), KotlinIcons.SCRIPT, "Kotlin Script"),
    Object(KotlinBundle.message("action.new.file.dialog.object.title"), KotlinIcons.OBJECT, "Kotlin Object")
}
