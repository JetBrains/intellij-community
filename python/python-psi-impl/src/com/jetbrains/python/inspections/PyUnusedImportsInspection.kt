package com.jetbrains.python.inspections

import com.google.common.collect.Sets
import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.Version
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.Processor
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix
import com.jetbrains.python.inspections.PyInspectionVisitor.getContext
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.psi.impl.stubs.evaluateVersionsForElement
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.*


class PyUnusedImportsInspection : PyInspection() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val visitor = Visitor(holder = holder,
                          myInspection = this,
                          typeEvalContext = getContext(session),
                          languageLevel = PythonLanguageLevelPusher.getLanguageLevelForFile(session.file))
    session.putUserData(KEY, visitor)
    return visitor
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
    val visitor = session.getUserData(KEY)
    checkNotNull(visitor)
    ReadAction.run<RuntimeException?>(
      ThrowableRunnable {
        if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
          visitor.highlightUnusedImports()
        }
        visitor.highlightImportsInsideGuards()
      }
    )
    session.putUserData(KEY, null)
  }

  class Visitor(
    holder: ProblemsHolder?,
    private val myInspection: PyInspection,
    typeEvalContext: TypeEvalContext,
    languageLevel: LanguageLevel,
  ) :
    PyInspectionVisitor(holder, typeEvalContext) {

    private val myAllImports = mutableSetOf<PyImportedNameDefiner>()
    private val myImportsInsideGuard = mutableSetOf<PyImportedNameDefiner>()
    private val myUsedImports = mutableSetOf<PyImportedNameDefiner>()
    private val myUnresolvedImports = mutableSetOf<PyImportedNameDefiner>()
    private val myVersion: Version = Version(languageLevel.majorVersion, languageLevel.minorVersion, 0)

    override fun visitPyImportElement(node: PyImportElement) {
      super.visitPyImportElement(node)
      val fromImport = PsiTreeUtil.getParentOfType<PyFromImportStatement?>(node, PyFromImportStatement::class.java)
      if (fromImport == null || !fromImport.isFromFuture()) {
        myAllImports.add(node)
      }
    }

    override fun visitPyStarImportElement(node: PyStarImportElement) {
      super.visitPyStarImportElement(node)
      myAllImports.add(node)
    }

    override fun visitComment(comment: PsiComment) {
      super.visitComment(comment)
      if (comment is PsiLanguageInjectionHost) {
        processInjection(comment as PsiLanguageInjectionHost)
      }
    }

    override fun visitPyElement(node: PyElement) {
      super.visitPyElement(node)
      if (node is PyReferenceOwner) {
        val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)
        processReference(node, node.getReference(resolveContext))
      }
      else {
        for (reference in node.getReferences()) {
          processReference(node, reference)
        }
      }
    }

    private fun processReference(node: PyElement, reference: PsiReference?) {
      if (reference == null || reference.isSoft()) {
        return
      }
      val guard = getImportErrorGuard(node)
      if (guard != null) {
        processReferenceInImportGuard(node, guard)
        return
      }
      var target: PsiElement? = null
      val unresolved: Boolean
      if (reference is PsiPolyVariantReference) {
        val resolveResults = reference.multiResolve(false)
        unresolved = (resolveResults.size == 0)
        for (resolveResult in resolveResults) {
          if (target == null && resolveResult.isValidResult()) {
            target = resolveResult.getElement()
          }
          if (resolveResult is ImportedResolveResult) {
            val definer = resolveResult.getDefiner()
            if (definer != null) {
              myUsedImports.add(definer)
            }
          }
        }
      }
      else {
        target = reference.resolve()
        unresolved = (target == null)
      }
      if (unresolved) {
        val ignoreUnresolved = ignoreUnresolved(node, reference) || !evaluateVersionsForElement(node).contains(myVersion)
        if (!ignoreUnresolved) {
          val severity = if (reference is PsiReferenceEx)
            reference.getUnresolvedHighlightSeverity(myTypeEvalContext)
          else
            HighlightSeverity.ERROR
          if (severity == null) return
        }
        // don't highlight unresolved imports as unused
        val importElement = node.getParent() as? PyImportElement
        if (importElement != null) {
          myUnresolvedImports.add(importElement)
        }
      }
    }

    private fun ignoreUnresolved(node: PyElement, reference: PsiReference): Boolean {
      var ignoreUnresolved = false
      for (extension in PyInspectionExtension.EP_NAME.extensionList) {
        if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
          ignoreUnresolved = true
          break
        }
      }
      return ignoreUnresolved
    }

    private fun processReferenceInImportGuard(node: PyElement, guard: PyExceptPart) {
      val importElement = PsiTreeUtil.getParentOfType<PyImportElement?>(node, PyImportElement::class.java)
      if (importElement != null) {
        val visibleName = importElement.getVisibleName()
        val owner = ScopeUtil.getScopeOwner(importElement)
        if (visibleName != null && owner != null) {
          val allWrites: MutableCollection<PsiElement?> = ScopeUtil.getElementsOfAccessType(visibleName, owner, ReadWriteInstruction.ACCESS.WRITE)
          val hasWriteInsideGuard = ContainerUtil.exists<PsiElement?>(allWrites, Condition { e: PsiElement? -> PsiTreeUtil.isAncestor(guard, e!!, false) })
          if (!hasWriteInsideGuard && !shouldSkipMissingWriteInsideGuard(guard, visibleName)) {
            myImportsInsideGuard.add(importElement)
          }
        }
      }
    }

    private fun shouldSkipMissingWriteInsideGuard(guard: PyExceptPart, name: String): Boolean {
      return isDefinedInParentScope(name, guard) || PyBuiltinCache.getInstance(guard).getByName(name) != null ||
             controlFlowAlwaysTerminatesInsideGuard(guard)
    }

    private fun isDefinedInParentScope(name: String, anchor: PsiElement): Boolean {
      return ScopeUtil.getDeclarationScopeOwner(ScopeUtil.getScopeOwner(anchor), name) != null
    }

    private fun controlFlowAlwaysTerminatesInsideGuard(guard: PyExceptPart): Boolean {
      val owner = ScopeUtil.getScopeOwner(guard)
      if (owner == null) return false
      val flow = ControlFlowCache.getControlFlow(owner)
      val instructions = flow.getInstructions()
      val start = ControlFlowUtil.findInstructionNumberByElement(instructions, guard.exceptClass)
      if (start <= 0) return false
      val canEscapeGuard = Ref.create<Boolean>(false)
      // TODO can we replace that with return ControlFlowUtil.process?
      ControlFlowUtil.process(instructions, start, Processor { instruction: Instruction ->
        val e = instruction.getElement()
        if (e != null && !PsiTreeUtil.isAncestor(guard, e, true)) {
          canEscapeGuard.set(true)
          return@Processor false
        }
        return@Processor true
      })
      return !canEscapeGuard.get()
    }

    fun getImportsInsideGuard(): MutableCollection<PyImportedNameDefiner> {
      return Collections.unmodifiableCollection(myImportsInsideGuard)
    }

    private fun getImportErrorGuard(node: PyElement?): PyExceptPart? {
      val importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase::class.java)
      if (importStatement != null) {
        val tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart::class.java)
        if (tryPart != null) {
          val tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement::class.java)
          if (tryExceptStatement != null) {
            for (exceptPart in tryExceptStatement.getExceptParts()) {
              val expr = exceptPart.getExceptClass()
              if (expr != null && "ImportError" == expr.getName()) {
                return exceptPart
              }
            }
          }
        }
      }
      return null
    }

    private fun processInjection(node: PsiLanguageInjectionHost?) {
      if (node == null) return
      val files = InjectedLanguageManager.getInstance(node.getProject()).getInjectedPsiFiles(node)
      if (files != null) {
        for (pair in files) {
          object : PyRecursiveElementVisitor() {
            override fun visitPyElement(element: PyElement) {
              super.visitPyElement(element)
              if (element is PyReferenceOwner) {
                val resolveContext = PyResolveContext.defaultContext(myTypeEvalContext)
                val reference = element.getReference(resolveContext)
                markTargetImportsAsUsed(reference)
              }
            }
          }.visitElement(pair.getFirst()!!)
        }
      }
    }

    private fun markTargetImportsAsUsed(reference: PsiPolyVariantReference) {
      val resolveResults = reference.multiResolve(false)
      for (resolveResult in resolveResults) {
        if (resolveResult is ImportedResolveResult) {
          val definer = resolveResult.getDefiner()
          if (definer != null) {
            myUsedImports.add(definer)
          }
        }
      }
    }

    fun highlightUnusedImports() {
      val extensions: List<PyInspectionExtension?> = PyInspectionExtension.EP_NAME.extensionList
      val unused: List<PsiElement> = collectUnusedImportElements()
      for (element in unused) {
        if (ContainerUtil.exists<PyInspectionExtension?>(extensions, Condition { extension: PyInspectionExtension? -> extension!!.ignoreUnused(element, myTypeEvalContext) })) {
          continue
        }
        if (!evaluateVersionsForElement(element).contains(myVersion)) {
          continue
        }
        if (element.getTextLength() > 0) {
          val fix = OptimizeImportsQuickFix()
          registerProblem(element, PyPsiBundle.message("INSP.unused.import.statement"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, fix)
        }
      }
    }

    fun highlightImportsInsideGuards() {
      val usedImportsInsideImportGuards: HashSet<PyImportedNameDefiner?> = Sets.newHashSet(getImportsInsideGuard())
      usedImportsInsideImportGuards.retainAll(myUsedImports)

      for (definer in usedImportsInsideImportGuards) {
        val importElement = PyUtil.`as`<PyImportElement?>(definer, PyImportElement::class.java)
        if (importElement == null) {
          continue
        }
        val asElement = importElement.getAsNameElement()
        val toHighlight: PyElement? = if (asElement != null) asElement else importElement.getImportReferenceExpression()
        registerProblem(toHighlight,
                        PyPsiBundle.message("INSP.try.except.import.error",
                                            importElement.getVisibleName()),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      }
    }

    fun collectUnusedImportElements(): List<PsiElement> {
      if (myAllImports.isEmpty()) {
        return emptyList()
      }
      // PY-1315 Unused imports inspection shouldn't work in python REPL console
      val first: PyImportedNameDefiner = myAllImports.first()
      if (first.getContainingFile() is PyExpressionCodeFragment || PythonRuntimeService.getInstance().isInPydevConsole(first)) {
        return emptyList()
      }
      val result: MutableList<PsiElement> = ArrayList<PsiElement>()

      val unusedImports: MutableSet<PyImportedNameDefiner> = HashSet(myAllImports)
      unusedImports.removeAll(myUsedImports)
      // TODO revise that
      unusedImports.removeAll(myUnresolvedImports)

      // Remove those unsed, that are reported to be skipped by extension points
      val unusedImportToSkip: MutableSet<PyImportedNameDefiner?> = HashSet<PyImportedNameDefiner?>()
      for (unusedImport in unusedImports) {
        if (ContainerUtil.exists<PyInspectionExtension>(PyInspectionExtension.EP_NAME.extensionList, Condition { o: PyInspectionExtension? -> o!!.ignoreUnusedImports(unusedImport) })) {
          unusedImportToSkip.add(unusedImport)
        }
      }

      unusedImports.removeAll(unusedImportToSkip)

      val usedImportNames: MutableSet<String?> = HashSet<String?>()
      for (usedImport in myUsedImports) {
        for (e in usedImport.iterateNames()) {
          usedImportNames.add(e.getName())
        }
      }

      val unusedStatements: MutableSet<PyImportStatementBase?> = HashSet<PyImportStatementBase?>()
      var packageQName: QualifiedName? = null
      var dunderAll: MutableList<String?>? = null

      // TODO: Use strategies instead of pack of "continue"
      iterUnused@ for (unusedImport in unusedImports) {
        if (packageQName == null) {
          val file = unusedImport.getContainingFile()
          if (file is PyFile) {
            dunderAll = file.getDunderAll()
          }
          if (file != null && PyUtil.isPackage(file)) {
            packageQName = QualifiedNameFinder.findShortestImportableQName(file)
          }
        }
        val importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase::class.java)
        if (importStatement != null && !unusedStatements.contains(importStatement) && !myUsedImports.contains(unusedImport)) {
          val inspection: PyInspection = checkNotNull(myInspection)
          if (inspection.isSuppressedFor(importStatement)) {
            continue
          }
          // don't remove as unused imports in try/except statements
          if (PsiTreeUtil.getParentOfType<PyTryExceptStatement?>(importStatement, PyTryExceptStatement::class.java) != null) {
            continue
          }
          // Don't report conditional imports as unused
          if (PsiTreeUtil.getParentOfType<PyIfStatement?>(unusedImport, PyIfStatement::class.java) != null) {
            for (e in unusedImport.iterateNames()) {
              if (usedImportNames.contains(e.getName())) {
                continue@iterUnused
              }
            }
          }
          val importedElement: PsiFileSystemItem?
          if (unusedImport is PyImportElement) {
            val element = unusedImport.resolve()
            if (element == null) {
              if (unusedImport.getImportedQName() != null) {
                //Mark import as unused even if it can't be resolved
                if (areAllImportsUnused(importStatement, unusedImports)) {
                  result.add(importStatement)
                }
                else {
                  result.add(unusedImport)
                }
              }
              continue
            }
            if (dunderAll != null && dunderAll.contains(unusedImport.getVisibleName())) {
              continue
            }
            importedElement = element.getContainingFile()
          }
          else {
            assert(importStatement is PyFromImportStatement)
            importedElement = (importStatement as PyFromImportStatement).resolveImportSource()
            if (importedElement == null) {
              continue
            }
          }
          if (packageQName != null && importedElement != null) {
            val importedQName = QualifiedNameFinder.findShortestImportableQName(importedElement)
            if (importedQName != null && importedQName.matchesPrefix(packageQName)) {
              continue
            }
          }
          if (unusedImport is PyStarImportElement || areAllImportsUnused(importStatement, unusedImports)) {
            unusedStatements.add(importStatement)
            result.add(importStatement)
          }
          else {
            result.add(unusedImport)
          }
        }
      }
      return result
    }

    private fun areAllImportsUnused(importStatement: PyImportStatementBase, unusedImports: MutableSet<PyImportedNameDefiner>): Boolean {
      val elements = importStatement.getImportElements()
      for (element in elements) {
        if (!unusedImports.contains(element)) {
          return false
        }
      }
      return true
    }

    fun optimizeImports() {
      val elementsToDelete: List<PsiElement> = collectUnusedImportElements()
      for (element in elementsToDelete) {
        PyPsiUtils.assertValid(element)
        element.delete()
      }
    }
  }

  companion object {
    private val KEY: Key<Visitor> = Key.create<Visitor>("PyUnusedImportsInspection.Visitor")
  }
}
