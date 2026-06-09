// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.intention

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.psi.MermaidBranchStatement
import com.intellij.mermaid.lang.psi.MermaidCheckoutStatement
import com.intellij.mermaid.lang.psi.MermaidCherryPickStatement
import com.intellij.mermaid.lang.psi.MermaidCommitIdValue
import com.intellij.mermaid.lang.psi.MermaidCommitStatement
import com.intellij.mermaid.lang.psi.MermaidDirective
import com.intellij.mermaid.lang.psi.MermaidElementFactory
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createBranchStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createCommitIdValue
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createCommitStatement
import com.intellij.mermaid.lang.psi.MermaidElementFactory.Companion.createParentIdAttribute
import com.intellij.mermaid.lang.psi.MermaidFile
import com.intellij.mermaid.lang.psi.MermaidMergeStatement
import com.intellij.mermaid.lang.psi.MermaidRecursiveVisitor
import com.intellij.mermaid.lang.psi.branchIdentifier
import com.intellij.mermaid.lang.psi.isQuoted
import com.intellij.mermaid.lang.psi.parentOfType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.addIfNotNull
import kotlin.random.Random


class GitGraphInspection : LocalInspectionTool() {
  companion object {
    private const val DEFAULT_BRANCH_NAME = "main"
  }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is MermaidFile) return null

    val mainBranchName = collectMainBranchName(file) ?: DEFAULT_BRANCH_NAME

    val result = mutableListOf<ProblemDescriptor>()
    file.accept(GitGraphProblemsCollector(manager, isOnTheFly, result, mainBranchName))

    return result.toTypedArray()
  }

  override fun runForWholeFile(): Boolean {
    return true
  }

  private fun collectMainBranchName(file: PsiFile): String? {
    val directive = SyntaxTraverser.psiTraverser(file).asSequence().filterIsInstance<MermaidDirective>().lastOrNull() ?: return null
    val injectedLanguageManager = InjectedLanguageManager.getInstance(directive.project)
    val injectedElement = injectedLanguageManager.findInjectedElementAt(directive.containingFile, directive.startOffset) ?: return null
    val directiveObject = injectedElement.parents(withSelf = true).filterIsInstance<JsonObject>().firstOrNull() ?: return null

    return SyntaxTraverser.psiTraverser(directiveObject)
      .asSequence()
      .filterIsInstance<JsonProperty>()
      .filter { it.name == "mainBranchName" }
      .map { it.value }
      .filterIsInstance<JsonStringLiteral>()
      .lastOrNull()
      ?.value
  }

  private class GitGraphProblemsCollector(
    private val manager: InspectionManager,
    private val isOnTheFly: Boolean,
    private val result: MutableList<ProblemDescriptor>,
    mainBranchName: String
  ) : MermaidRecursiveVisitor() {
    private var currentBranchName = mainBranchName
    private val commits = mutableMapOf<String, Commit>()
    private var head: Commit? = null
    private val branches = mutableMapOf<String, String?>()

    private var seq = 0

    init {
      branches[currentBranchName] = null
    }

    data class Commit(val id: String, val parents: List<String?>, val branch: String, val isMergeCommit: Boolean = false)

    private fun addCommit(commit: Commit, commitElement: MermaidCommitIdValue?) {
      head = commit
      if (commits.put(commit.id, commit) == null) {
        branches[currentBranchName] = commit.id
      } else if (commitElement != null && !commit.isMergeCommit){
        result.add(
          manager.createProblemDescriptor(
            commitElement,
            MermaidBundle.message("git.graph.inspection.commit.id.already.exists"),
            isOnTheFly,
            emptyArray(),
            ProblemHighlightType.WARNING
          )
        )
      }
    }

    override fun visitCommitStatement(commitStatement: MermaidCommitStatement) {
      val commitElement = commitStatement.commitIdAttribute?.commitIdValue
      val id = commitElement?.text ?: generateCommitId()
      val parents = head?.let { listOf(it.id) } ?: listOf()
      val commit = Commit(id, parents, currentBranchName)
      addCommit(commit, commitElement)
    }

    override fun visitBranchStatement(branchStatement: MermaidBranchStatement) {
      val branchElement = branchStatement.branchIdentifier()
      val branch = branchElement.text
      if (!branches.contains(branch)) {
        branches[branch] = head?.id
        checkoutBranch(branch)
      } else {
        result.add(
          manager.createProblemDescriptor(
            branchElement,
            MermaidBundle.message("git.graph.inspection.branch.already.exists"),
            isOnTheFly,
            emptyArray(),
            ProblemHighlightType.ERROR
          )
        )
      }
    }

    override fun visitMergeStatement(mergeStatement: MermaidMergeStatement) {
      val branchElement = mergeStatement.branchIdentifier()
      val branch = branchElement.text
      val commitElement = mergeStatement.commitIdAttribute?.commitIdValue
      val mergeCommitId = commitElement?.text

      val currentCommit = branches[currentBranchName]
      val branchCommit = branches[branch]

      result.addIfNotNull(when {
        currentBranchName == branch -> {
            manager.createProblemDescriptor(
              branchElement,
              MermaidBundle.message("git.graph.inspection.cannot.merge.a.branch.to.itself"),
              isOnTheFly,
              emptyArray(),
              ProblemHighlightType.ERROR
            )
        }

        currentCommit == null -> {
            manager.createProblemDescriptor(
              mergeStatement,
              MermaidBundle.message("git.graph.inspection.branch.has.no.commits", branch),
              isOnTheFly,
              emptyArray(),
              ProblemHighlightType.ERROR
            )
        }

        !branches.contains(branch) -> {
            manager.createProblemDescriptor(
              branchElement,
              MermaidBundle.message("git.graph.inspection.branch.does.not.exist", branch),
              isOnTheFly,
              arrayOf(AddBranchStatementFix(branch, mergeStatement.isQuoted())),
              ProblemHighlightType.ERROR
            )
        }

        branchCommit == null -> {
            manager.createProblemDescriptor(
              branchElement,
              MermaidBundle.message("git.graph.inspection.branch.has.no.commits", branch),
              isOnTheFly,
              emptyArray(),
              ProblemHighlightType.ERROR
            )
        }

        currentCommit == branchCommit -> {
            manager.createProblemDescriptor(
              branchElement,
              MermaidBundle.message("git.graph.inspection.branches.have.same.head", branch, currentBranchName),
              isOnTheFly,
              emptyArray(),
              ProblemHighlightType.ERROR
            )
        }

        mergeCommitId != null && commits.contains(mergeCommitId) -> {
          manager.createProblemDescriptor(
            commitElement,
            MermaidBundle.message("git.graph.inspection.commit.id.already.exists"),
            isOnTheFly,
            emptyArray(),
            ProblemHighlightType.ERROR
          )
        }

        else -> {
          val id = mergeCommitId ?: generateCommitId()
          val parents = head?.let { listOf(it.id, branches[branch]) } ?: listOf(null)
          val commit = Commit(id, parents, currentBranchName, isMergeCommit = true)
          addCommit(commit, commitElement)
          null
        }
      })
    }

    override fun visitCherryPickStatement(cherryPickStatement: MermaidCherryPickStatement) {
      val commitElement = cherryPickStatement.commitIdAttribute.commitIdValue
      val id = commitElement.text
      val commit = commits[id]
      if (commit == null) {
        result.add(
          manager.createProblemDescriptor(
            commitElement,
            MermaidBundle.message("git.graph.inspection.commit.id.does.not.exist"),
            isOnTheFly,
            arrayOf(AddCommitStatementFix(id)),
            ProblemHighlightType.ERROR
          )
        )
        return
      }
      val branch = commit.branch
      val parentCommitElement = cherryPickStatement.parentCommitIdAttribute?.commitIdValue
      val parentCommitId = parentCommitElement?.text

      when {
        parentCommitId != null && !commit.parents.contains(parentCommitId) -> {
          result.add(
            manager.createProblemDescriptor(
              parentCommitElement,
              MermaidBundle.message("git.graph.inspection.specified.parent.commit.is.not.immediate"),
              isOnTheFly,
              commit.parents.filterNotNull().map { ReplaceParentCommitFix(it) }.toTypedArray(),
              ProblemHighlightType.ERROR
            )
          )
        }

        commit.isMergeCommit && parentCommitId == null -> {
          result.add(
            manager.createProblemDescriptor(
              cherryPickStatement,
              MermaidBundle.message("git.graph.inspection.immediate.parent.commit.must.be.specified"),
              isOnTheFly,
              commit.parents.filterNotNull().map { SpecifyParentCommitFix(it) }.toTypedArray(),
              ProblemHighlightType.ERROR
            )
          )
        }

        branch == currentBranchName -> {
          result.add(
            manager.createProblemDescriptor(
              cherryPickStatement,
              MermaidBundle.message("git.graph.inspection.source.commit.is.already.on.current.branch"),
              isOnTheFly,
              emptyArray(),
              ProblemHighlightType.ERROR
            )
          )
        }

        else -> {
          if (branches[currentBranchName] == null) {
            result.add(
              manager.createProblemDescriptor(
                cherryPickStatement,
                MermaidBundle.message("git.graph.inspection.branch.has.no.commits", currentBranchName),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.ERROR
              )
            )
          } else {
            val id = generateCommitId()
            val parents = head?.let { listOf(it.id, commit.id) } ?: listOf(null)
            val commit = Commit(id, parents, currentBranchName)
            addCommit(commit, commitElement)
          }
        }
      }
    }

    override fun visitCheckoutStatement(checkoutStatement: MermaidCheckoutStatement) {
      val branch = checkoutStatement.branchIdentifier().text
      if (!branches.contains(branch)) {
        result.add(
          manager.createProblemDescriptor(
            checkoutStatement.branchIdentifier(),
            MermaidBundle.message("git.graph.inspection.branch.does.not.exist", branch),
            isOnTheFly,
            arrayOf(AddBranchStatementFix(branch, checkoutStatement.isQuoted())),
            ProblemHighlightType.ERROR
          )
        )
      } else {
        checkoutBranch(branch)
      }
    }

    private fun checkoutBranch(branch: String) {
      currentBranchName = branch
      head = commits[branches[currentBranchName]]
    }

    private fun generateCommitId(): String {
      return "${seq++}_${Random(42).nextInt()}"
    }
  }

  private class AddBranchStatementFix(
    private val branchName: String,
    private val isQuoted: Boolean
  ): LocalQuickFix {
    private val quote: String = "\""
    override fun getFamilyName() = MermaidBundle.message("fix.create.branch.declaration", branchName)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      val statement = element.parentOfType(type = MermaidTokenTypeSets.STATEMENTS) ?: return
      val document = element.parentOfType(type = MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS) ?: return

      val name = buildString {
        if (isQuoted) append(quote)
        append(branchName.replace(" ", "\\\\ "))
        if (isQuoted) append(quote)
      }

      val declaration = createBranchStatement(project, name)?: return

      document.addBefore(declaration, statement)
      document.addBefore(MermaidElementFactory.createEOL(project), statement)
    }
  }

  private class AddCommitStatementFix(
    private val commitId: String
  ): LocalQuickFix {
    override fun getFamilyName() = MermaidBundle.message("fix.create.commit.declaration", commitId)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      val statement = element.parentOfType(type = MermaidTokenTypeSets.STATEMENTS) ?: return
      val document = element.parentOfType(type = MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS) ?: return

      val name = commitId.replace(" ", "\\\\ ")

      val declaration = createCommitStatement(project, name) ?: return

      document.addBefore(declaration, statement)
      document.addBefore(MermaidElementFactory.createEOL(project), statement)
    }
  }

  private class ReplaceParentCommitFix(
    private val commitId: String
  ): LocalQuickFix {
    override fun getFamilyName() = MermaidBundle.message("fix.replace.parent.commit.id", commitId)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      val newId = createCommitIdValue(project, commitId) ?: return
      element.replace(newId)
    }
  }

  private class SpecifyParentCommitFix(
    private val commitId: String
  ): LocalQuickFix {
    override fun getFamilyName() = MermaidBundle.message("fix.specify.parent.commit.id", commitId)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      val parentIdAttribute = createParentIdAttribute(project, commitId) ?: return

      element.add(parentIdAttribute)
    }
  }
}
