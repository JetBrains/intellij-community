package org.intellij.plugins.markdown.lang.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

/**
 * Index of header anchors.
 */
class HeaderAnchorIndex: StringStubIndexExtension<MarkdownHeader>() {
  override fun getKey(): StubIndexKey<String, MarkdownHeader> {
    return KEY
  }

  companion object {
    @JvmField
    val KEY: StubIndexKey<String, MarkdownHeader> = StubIndexKey.createIndexKey("markdown.header.anchor")

    /**
     * @param anchorText Expected not to contain starting `#`.
     */
    fun collectHeaders(project: Project, scope: GlobalSearchScope, anchorText: String): Collection<MarkdownHeader> {
      return StubIndex.getElements(KEY, anchorText, project, scope, MarkdownHeader::class.java)
    }

    fun collectAllAnchors(project: Project, scope: GlobalSearchScope): Collection<String> {
      val result = ArrayList<String>()
      val all = mutableListOf<String>()
      StubIndex.getInstance().processAllKeys(
        KEY,
        Processor {
          all.add(it)
          if (collectHeaders(project, scope, it).isNotEmpty()) {
            result.add(it)
          }
          return@Processor true
        },
        scope
      )
      return result
    }
  }
}
