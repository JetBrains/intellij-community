package com.intellij.markdown.backend.reference

import com.intellij.markdown.backend.reference.headers.HeaderAnchorPathReferenceProvider
import com.intellij.openapi.paths.PathReferenceProviderBase
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ProcessingContext
import com.intellij.util.io.URLUtil
import org.intellij.plugins.markdown.lang.references.ReferenceUtil
import org.intellij.plugins.markdown.lang.references.paths.FileWithoutExtensionReference
import org.intellij.plugins.markdown.lang.references.paths.github.GithubWikiLocalFileReference
import org.jetbrains.ide.parseHostAndPath
import org.jetbrains.ide.readGitRemoteUrls
import java.util.regex.Pattern

internal class GithubWikiLocalFileReferenceProvider: PsiReferenceProvider() {
  private val anchorReferenceProvider = HeaderAnchorPathReferenceProvider()

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val text = element.text
    val link = parseLink(text) ?: return emptyArray()
    val root = findLocalRoot(element, link) ?: return emptyArray()
    val pathRange = link.pathRange
    val encodedPath = pathRange.substring(text)
    val decodedPath = URLUtil.unescapePercentSequences(encodedPath)
    val referenceSet = createReferenceSet(element, pathRange.startOffset, encodedPath, root)
    val references = referenceSet.allReferences.toMutableList<PsiReference>()
    val fileReference = ReferenceUtil.findFileReference(references)
    if (fileReference != null) {
      addReferenceWithoutExtension(element, root, decodedPath, fileReference, references)
    }
    anchorReferenceProvider.createReferences(element, references, ARE_REFERENCES_SOFT)
    return references.map { GithubWikiLocalFileReference(it) }.toTypedArray()
  }

  private fun parseLink(text: String): WikiLink? {
    val matcher = linkPattern.matcher(text)
    if (!matcher.lookingAt()) return null
    val start = matcher.end()
    val end = PathReferenceProviderBase.getLastPosOfURL(start, text).takeIf { it >= 0 } ?: text.length
    val host = if (matcher.group(1) == null) null else parseHostAndPath(text)?.first ?: return null
    return WikiLink(host, matcher.group(2), matcher.group(3), TextRange(start, end))
  }

  private fun findLocalRoot(element: PsiElement, link: WikiLink): VirtualFile? {
    val sourceFile = element.containingFile?.virtualFile ?: return null
    val root = generateSequence(sourceFile.parent) { it.parent }
      .firstOrNull { it.findChild(".git") != null }
      ?: return null
    val rootPath = root.fileSystem.getNioPath(root) ?: return null
    return root.takeIf { readGitRemoteUrls(rootPath).any { hasMatchingRepository(it.url, link) } }
  }

  private fun hasMatchingRepository(remoteUrl: String, link: WikiLink): Boolean {
    val (host, path) = parseHostAndPath(remoteUrl) ?: return false
    val repositoryPath = path.trim('/').removeSuffix(".git").removeSuffix(".wiki")
    return (link.host == null || link.host.equals(host, ignoreCase = true)) &&
           repositoryPath.equals("${link.owner}/${link.repository}", ignoreCase = true)
  }

  private fun createReferenceSet(element: PsiElement, offset: Int, path: String, root: VirtualFile): FileReferenceSet {
    return object: FileReferenceSet(path, element, offset, this, true, true, null) {
      override fun isUrlEncoded() = true

      override fun isSoft() = ARE_REFERENCES_SOFT

      override fun computeDefaultContexts(): Collection<PsiFileSystemItem> = listOfNotNull(element.manager.findDirectory(root))
    }
  }

  private fun addReferenceWithoutExtension(
    element: PsiElement,
    root: VirtualFile,
    decodedPath: String,
    fileReference: FileReference,
    references: MutableList<PsiReference>,
  ) {
    val preferFileWithExtension = !decodedPath.endsWith(".md")
    if (preferFileWithExtension && root.findFileByRelativePath("$decodedPath.md")?.isDirectory == false) {
      references.remove(fileReference)
    }
    references.add(RootRelatedFileWithoutExtensionReference(element, fileReference, root, preferFileWithExtension))
  }

  companion object {
    private val linkPattern = Pattern.compile("^(?:https://([^/]+))?/([^/]+)/([^/]+)/wiki/")
    private const val ARE_REFERENCES_SOFT = false

    internal fun isDomainlessLink(text: String): Boolean = text.startsWith('/') && linkPattern.matcher(text).lookingAt()
  }

  private data class WikiLink(val host: String?, val owner: String, val repository: String, val pathRange: TextRange)

  private class RootRelatedFileWithoutExtensionReference(
    element: PsiElement,
    fileReference: FileReference,
    private val root: VirtualFile,
    private val preferFileWithExtension: Boolean,
  ): FileWithoutExtensionReference(element, fileReference, ARE_REFERENCES_SOFT) {
    override fun findReferencedFile(): VirtualFile? = root.findFileByRelativePath(decodedPath)?.takeUnless { it.isDirectory }

    override fun resolve(): PsiElement? {
      if (!preferFileWithExtension) return super.resolve()
      return findReferencedFile()?.let { PsiUtilCore.getPsiFile(element.project, it) }
    }
  }
}
