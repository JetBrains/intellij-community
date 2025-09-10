package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.psi.PsiFileSystemItem
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileGroupFeatureProvider.Fields.FILE_GROUP
import com.intellij.util.asSafely

class SearchEverywhereFileGroupFeatureProvider : SearchEverywhereElementFeaturesProvider(
  FileSearchEverywhereContributor::class.java,
  RecentFilesSEContributor::class.java) {

  object Fields {
    val FILE_GROUP = EventFields.Enum("fileGroup", FileGroup::class.java)
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(FILE_GROUP)

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    return SearchEverywherePsiElementFeaturesProviderUtils.getPsiElementOrNull(element)
      .asSafely<PsiFileSystemItem>()
      ?.name
      ?.let { FileGroup.findGroup(it) }
      ?.let { listOf(FILE_GROUP.with(it)) } ?: return emptyList()
  }

  enum class FileGroup(private val validators: List<GroupAcceptor>) {
    MAIN(
      accept("app").withExtension(".css", ".js", ".module.ts", ".py", ".test.js", ".tsx", ".vue"),
      accept("index").withAnyExtension(),
      accept("main").withAnyExtension(),
      accept("util").withAnyExtension(),
      accept("utils").withAnyExtension()
    ),

    BUILD(
      accept("build").withExtension("", ".gradle", ".gradle.kts", ".js", ".sh", ".xml", ".yml"),
      accept("dockerfile").withNoExtension(),
      accept("makefile").withExtension("", ".am", ".in"),
      accept("cmakelists").withExtension(".txt"),
      accept("pom").withExtension(".xml"),
      accept("plugin").withExtension(".xml"),
      accept("setup").withExtension(".py"),
      accept("gemfile").withNoExtension(),
    ),

    CHANGELOG(
      accept("changelog").withAnyExtension(),
      accept("history").withAnyExtension(),
    ),

    CONFIG(
      accept("config").withAnyExtension()
    ),

    README(
      accept("readme").withAnyExtension(),
    );

    constructor(vararg validator: GroupAcceptor) : this(validator.toList())

    fun accepts(filenameWithExtension: String): Boolean = this.validators.any { it.accepts(filenameWithExtension) }

    companion object {
      fun findGroup(filenameWithExtension: String): FileGroup? = FileGroup.values()
        .find { it.accepts(filenameWithExtension.lowercase()) }
    }
  }
}

private class GroupAcceptor private constructor(private val acceptedFilename: String,
                                                private val extensionAcceptor: ExtensionAcceptor) {
  fun accepts(filenameWithExtension: String): Boolean {
    val dot = filenameWithExtension.indexOf(".").takeIf { it >= 0 } ?: filenameWithExtension.length
    val baseName = filenameWithExtension.substring(0, dot)
    val extension = filenameWithExtension.substring(dot)

    return accepts(baseName, extension)
  }

  private fun accepts(baseName: String, extension: String): Boolean = baseName == acceptedFilename && extensionAcceptor.accepts(extension)

  class FilenameAcceptor(val acceptedFilename: String) {
    fun withNoExtension(): GroupAcceptor = GroupAcceptor(acceptedFilename,
                                                         ExtensionAcceptor.NoExtension)

    fun withExtension(vararg acceptedExtensions: String): GroupAcceptor = GroupAcceptor(acceptedFilename,
                                                                                        ExtensionAcceptor.DefinedList(acceptedExtensions.toList()))

    fun withAnyExtension(): GroupAcceptor = GroupAcceptor(acceptedFilename,
                                                          ExtensionAcceptor.AnyExtension)
  }

  private sealed interface ExtensionAcceptor {
    fun accepts(extension: String): Boolean

    class DefinedList(val acceptedExtensions: Collection<String>) : ExtensionAcceptor {
      override fun accepts(extension: String): Boolean {
        return extension in acceptedExtensions
      }
    }

    object AnyExtension : ExtensionAcceptor {
      override fun accepts(extension: String): Boolean {
        return true
      }
    }

    object NoExtension : ExtensionAcceptor {
      override fun accepts(extension: String): Boolean = extension.isBlank()
    }
  }
}

private fun accept(filenameWithoutExtension: String): GroupAcceptor.FilenameAcceptor = GroupAcceptor.FilenameAcceptor(filenameWithoutExtension)
