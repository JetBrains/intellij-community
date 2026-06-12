package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.idea.base.psi.setCallableReceiverTypeReference
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse

/**
 * Runs [moveToClassWithConversion] in a command.
 *
 * Sub-refactorings inside are undo-transparent.
 * In case the operations don't finish completely, the partially applied actions are undone.
 * The undo checks that the operation on the stack is the unfinished move command.
 */
internal fun runMoveToClassWithConversionCommand(
    project: Project,
    targetClassCandidateParameter: TargetClassCandidateParameter,
    functionToMove: KtNamedFunction,
    targetClass: KtClassOrObject,
    editor: FileEditor
) {
    var isSuccessful = false
    val commandName = KotlinBundle.message(
        "refactoring.move.to.class.command.name",
        functionToMove.name ?: "<unnamed declaration>",
        targetClass.name ?: "<unnamed class>",
    )
    try {
        CommandProcessor.getInstance().executeCommand(
            project,
            Runnable {
                CommandProcessor.getInstance().runUndoTransparentAction {
                    isSuccessful = moveToClassWithConversion(functionToMove, targetClass, targetClassCandidateParameter)
                }
            },
            /* name = */ commandName, /* groupId = */ null,
            UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION,
        )
    } finally {
        if (!isSuccessful) {
            undoMoveCommand(commandName, project, editor)
        }
    }
}

/**
 * Undo the last operation if the action to be undone matches the command name.
 */
private fun undoMoveCommand(commandName: String, project: Project, editor: FileEditor) {
    val undoManager = UndoManager.getInstance(project)
    if (undoManager.isUndoAvailable(editor)) {
        val undoActionDescription = undoManager.getUndoActionNameAndDescription(editor).second
        val stubParam = "__stub__"
        val undoActionPrefix = ActionsBundle.message("action.undo.description", stubParam).substringBeforeLast(stubParam)
        val undoActionDescriptionNoPrefix = undoActionDescription.removePrefix(undoActionPrefix)
        if (undoActionDescriptionNoPrefix == commandName) {
            undoManager.undo(editor)
        }
    }
}

/**
 * Moves [functionToMove] into [targetClass].
 *
 * The operation consists of the following steps:
 * * If [functionToMove] is a member function, it is moved to the top level.
 *   This step is necessary to correctly update usages with explicit receiver on the next steps.
 * * If [targetClassCandidateParameter] is not an extension receiver, it is turned into the extension receiver via Change Signature.
 * * The extension is moved into the target class.
 * * The extension receiver type reference is dropped, obsolete labels are dropped from labeled `this` expressions in the function body.
 *
 * @return whether all the steps have finished successfully
 */
private fun moveToClassWithConversion(
    functionToMove: KtNamedFunction,
    targetClass: KtClassOrObject,
    targetClassCandidateParameter: TargetClassCandidateParameter,
): Boolean {
    val isTopLevel = functionToMove.isTopLevel
    val topLevelFunction = if (isTopLevel) functionToMove else moveToTopLevel(functionToMove)?.element ?: return false
    val changeSignatureFunctionPointer = topLevelFunction.createSmartPointer()
    convertTargetParameterToExtensionReceiver(topLevelFunction, targetClassCandidateParameter).ifFalse { return false }
    val functionAfterConversion = changeSignatureFunctionPointer.element ?: return false
    val movedFunction = moveToClass(functionAfterConversion, targetClass)?.element ?: return false
    removeReceiverTypeReference(movedFunction.project, movedFunction)
    return true
}

/**
 * Runs the Change Signature refactoring to turn a value/context parameter into the extension receiver.
 * @return whether the conversion finished successfully.
 */
private fun convertTargetParameterToExtensionReceiver(
    functionToMove: KtNamedFunction,
    candidate: TargetClassCandidateParameter,
): Boolean {
    val methodDescriptor = KotlinMethodDescriptor(functionToMove)
    val changeInfo = KotlinChangeInfo(methodDescriptor)

    when (candidate.kind) {
        TargetClassCandidateKind.VALUE_PARAMETER -> {
            val parameterName = candidate.parameterName ?: return false
            val changedParam = changeInfo.getNonReceiverParameters().find { it.oldName == parameterName }
            changeInfo.receiverParameterInfo = changedParam
        }

        TargetClassCandidateKind.CONTEXT_PARAMETER -> {
            val parameterName = candidate.parameterName ?: return false
            val changedParam = changeInfo.getNonReceiverParameters().find { it.oldName == parameterName } ?: return false
            changedParam.isContextParameter = false
            changeInfo.receiverParameterInfo = changedParam
        }

        TargetClassCandidateKind.EXTENSION_RECEIVER -> {}
    }

    var isSuccessful = false
    val changeSignatureProcessor = object : KotlinChangeSignatureProcessor(functionToMove.project, changeInfo) {}
    changeSignatureProcessor.prepareSuccessfulSwingThreadCallback = Runnable {
        isSuccessful = true
    }
    changeSignatureProcessor.run()
    return isSuccessful
}

/**
 * Runs the Move refactoring to lift [functionToMove] to the top level of the containing file.
 *
 * Dispatch receiver is turned into the first parameter during such a move.
 * @return a pointer to the moved function.
 */
private fun moveToTopLevel(functionToMove: KtNamedFunction): SmartPsiElementPointer<KtNamedFunction>? {
    val moveDescriptor = K2MoveDescriptor.Declarations(
        project = functionToMove.project,
        source = K2MoveSourceDescriptor.ElementSource(setOf(functionToMove)),
        target = K2MoveTargetDescriptor.File(functionToMove.containingKtFile)
    )
    val descriptor = K2MoveOperationDescriptor.Declarations(
        functionToMove.project,
        listOf(moveDescriptor),
        searchForText = false,
        searchInComments = false,
        searchReferences = true,
        dirStructureMatchesPkg = false,
    )
    return moveFunctionAndFindTheResult(functionToMove, descriptor)
}

/**
 * Runs the Move refactoring to put [functionToMove] into the [targetClass].
 * @return a pointer to the moved function
 */
internal fun moveToClass(
    functionToMove: KtNamedFunction,
    targetClass: KtClassOrObject
): SmartPsiElementPointer<KtNamedFunction>? {
    val moveDescriptor = K2MoveDescriptor.Declarations(
        project = functionToMove.project,
        source = K2MoveSourceDescriptor.ElementSource(setOf(functionToMove)),
        target = K2MoveTargetDescriptor.ClassOrObject(targetClass)
    )

    val descriptor = K2MoveOperationDescriptor.Declarations(
        functionToMove.project,
        listOf(moveDescriptor),
        searchForText = false,
        searchInComments = false,
        searchReferences = true,
        dirStructureMatchesPkg = false,
    )

    return moveFunctionAndFindTheResult(functionToMove, descriptor)
}

/**
 * Runs the move refactoring and searches for the moved function by name among the moved declarations.
 */
private fun moveFunctionAndFindTheResult(
    functionToMove: KtNamedFunction,
    descriptor: K2MoveOperationDescriptor.Declarations
): SmartPsiElementPointer<KtNamedFunction>? {
    val functionName = functionToMove.name ?: return null
    var movedFunctionPointer: SmartPsiElementPointer<KtNamedFunction>? = null
    val processor = object: K2MoveDeclarationsRefactoringProcessor(descriptor) {
        override fun postDeclarationMoved(originalDeclaration: KtNamedDeclaration, newDeclaration: KtNamedDeclaration) {
            movedFunctionPointer = (newDeclaration as? KtNamedFunction)
                ?.takeIf { it.name == functionName }
                ?.createSmartPointer()
        }
    }
    processor.prepareSuccessfulSwingThreadCallback = Runnable {}
    processor.run()
    return movedFunctionPointer
}

/**
 * Removes the receiver type reference from the extension function and updates labeled `this` expressions.
 * [movedFunction] should be a member extension function in the same class as the extension receiver.
 */
private fun removeReceiverTypeReference(project: Project, movedFunction: KtNamedFunction) {
    WriteCommandAction.runWriteCommandAction(project, Computable {
        movedFunction.descendantsOfType<KtThisExpression>().forEach {
            it.getTargetLabel()?.takeIf { label -> label.getReferencedNameElement().text == movedFunction.name }?.delete()
        }
        movedFunction.setCallableReceiverTypeReference(null)
    })
}
