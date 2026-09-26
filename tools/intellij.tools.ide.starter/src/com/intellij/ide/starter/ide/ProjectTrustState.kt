package com.intellij.ide.starter.ide

import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.starter.models.IdeInfoType
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.childElement
import com.intellij.ide.starter.utils.componentOption
import com.intellij.ide.starter.utils.editXmlConfig
import com.intellij.ide.starter.utils.orCreateComponentOption
import com.intellij.ide.starter.utils.removeChildElements
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.util.xmlb.Constants
import com.intellij.util.xmlb.annotations.OptionTag
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.nio.file.Path
import kotlin.apply
import kotlin.io.path.exists
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

/**
 * Mirrors the [com.intellij.util.ThreeState] returned by [com.intellij.ide.trustedProjects.TrustedProjects.getProjectTrustedState].
 */
enum class ProjectTrustState {
  /** The IDE trusts the project. The starter default. */
  TRUSTED,

  /**
   * The IDE does not trust the project, it is opened in safe mode and no dialog is shown.
   * Not supported for Rider: no Rider container keeps a distrusted path.
   */
  NOT_TRUSTED,

  /** The IDE asks about trust on project opening, the `Trust Project?` dialog is shown. */
  ASK,
}

fun IDETestContext.setProjectTrusted(projectPath: Path? = null, configPath: Path = paths.configDir): IDETestContext =
  setProjectTrustState(ProjectTrustState.TRUSTED, projectPath, configPath)


// Removing this file allows to run IDE with Safe Mode dialog.
fun IDETestContext.deleteTrustedPathsXml(): IDETestContext = apply {
  allTrustStorages(paths.configDir).forEach { it.file.deleteRecursivelyQuietly() }
}

/**
 * Declares the trust state the test wants the project to be opened in.
 *
 * The starter default is [ProjectTrustState.TRUSTED].
 * [com.intellij.ide.starter.runner.TestContainer.applyDefaultVMOptions] sets it unconditionally, so a call is only
 * needed to change that default.
 *
 * The record goes to the side that opens the real project path: a monolith, or the backend of a split run. A
 * frontend needs none. Use `disableProjectTrustChecks` to turn the check of a light frontend off.
 *
 * Use [addTrustedPath] to trust a whole directory instead of a single project.
 *
 * @param projectPath the project path, if it differs from [IDETestContext.resolvedProjectHome]
 * @param configPath the config to patch, if it is not [IDEDataPaths.configDir]
 */
fun IDETestContext.setProjectTrustState(
  state: ProjectTrustState,
  projectPath: Path? = null,
  configPath: Path = paths.configDir,
): IDETestContext {
  // The platform matches a record by the path components, so a path that holds `..` never matches.
  val path = when {
    projectPath != null -> projectPath.normalize()
    testCase.projectInfo == NoProject -> null
    else -> resolvedProjectHome.normalize()
  }

  // a project with no record already asks about trust, but NOT_TRUSTED has no other way to say what it wants
  require(path != null || state != ProjectTrustState.NOT_TRUSTED) {
    "$state needs a project. The context of '$testName' has none, and the call gave no projectPath, so there " +
    "is no path to hold the state."
  }

  if (path != null && !testCase.ideInfo.isFrontend) {
    when (state) {
      ProjectTrustState.ASK -> clearTrustRecords(path, allTrustStorages(configPath))
      else -> projectStateStorage(configPath).writeRecord(state, path)
    }
  }
  return this
}

/**
 * Adds [directory] to the trusted paths of the IDE, so the IDE trusts every project under it. This covers a project
 * the IDE creates later, whose path the test cannot know. Mirrors
 * [com.intellij.ide.impl.TrustedPathsSettings.addTrustedPath].
 *
 * Use [setProjectTrustState] for a single project. There is no distrusted counterpart.
 */
fun IDETestContext.addTrustedPath(directory: Path): IDETestContext {
  trustedPathSettingStorage(paths.configDir).writeRecord(ProjectTrustState.TRUSTED, directory.normalize())
  return this
}

/** Puts one record for the normalized [path] into this storage, and drops the record [path] already has there. */
private fun TrustStorage.writeRecord(state: ProjectTrustState, path: Path) {
  require(state != ProjectTrustState.NOT_TRUSTED || holdsStatePerPath) {
    "$state cannot be expressed in $component: the container keeps trusted paths only"
  }

  editXmlConfig(file, APPLICATION_TAG) { xmlDoc ->
    val container = xmlDoc.orCreateComponentOption(component, option, containerTag)
    // dropping the record makes the method idempotent and lets it override the starter default
    container.removeRecords { recorded -> recorded == path }
    container.appendChild(newRecord(xmlDoc, path, trusted = state == ProjectTrustState.TRUSTED))
  }
}

/**
 * Drops from [storagesToCleanup] every record that resolves the trust state of the normalized [path], which is what
 * [ProjectTrustState.ASK] asks for. The platform reads the record of any ancestor of [path], so every ancestor goes.
 */
private fun clearTrustRecords(path: Path, storagesToCleanup: List<TrustStorage>) {
  for ((file, storages) in storagesToCleanup.groupBy { it.file }) {
    // a missing file holds no record, so there is nothing to clear there
    if (!file.exists()) continue

    editXmlConfig(file, APPLICATION_TAG) { xmlDoc ->
      for (storage in storages) {
        xmlDoc.componentOption(storage.component, storage.option, storage.containerTag)
          ?.removeRecords { recorded -> path.startsWith(recorded) }
      }
      // the Rider flag trusts every solution, so it hides the dialog on its own
      xmlDoc.documentElement.childElement(ComponentStorageUtil.COMPONENT, TRUSTED_SOLUTION_STORE)
        ?.childElement(Constants.OPTION, TRUST_EVERYTHING_OPTION)
        ?.let { it.parentNode.removeChild(it) }
    }
  }
}

/** Drops every child record whose path [resolvesState] accepts. */
private fun Element.removeRecords(resolvesState: (Path) -> Boolean) =
  removeChildElements { record -> record.recordedPath()?.let(resolvesState) == true }

/**
 * The normalized path one record holds: the `key` of a map entry, or the `value` of a list or set option. A value
 * this runtime cannot parse as a path gives `null`.
 */
private fun Element.recordedPath(): Path? =
  getAttribute(Constants.KEY).ifEmpty { getAttribute(Constants.VALUE) }
    .takeIf { it.isNotEmpty() }
    ?.let { runCatching { Path.of(it).normalize() }.getOrNull() }

/**
 * Where the IDE keeps one kind of trust record. A config file nests the four fields in this order:
 *
 * ```xml
 * <application>
 *   <component name="Trusted.Paths">                <!-- component -->
 *     <option name="TRUSTED_PROJECT_PATHS">         <!-- option -->
 *       <map>                                       <!-- containerTag -->
 *         <entry key="/path/to/project" value="true"/>
 *       </map>
 *     </option>
 *   </component>
 * </application>
 * ```
 */
private data class TrustStorage(
  /** The config file that holds [component]. One file can hold more than one component. */
  val file: Path,
  /**
   * The name of the platform component, which is the `name` of its `@State`. One component groups every setting of
   * one state class, so [component] says which class owns the records.
   */
  val component: String,
  /**
   * The serialized name of the one state property that holds the records, which is the `value` of its `@OptionTag`.
   * A component holds one `option` element per property, so [option] says which setting of [component] to touch.
   */
  val option: String,
  /** The tag of the element below [option] that holds the records. The serializer names it after the property type. */
  val containerTag: String,
) {
  /** Whether the container holds a state per path. A map does. A list and a set hold a trusted path only. */
  val holdsStatePerPath: Boolean get() = containerTag == Constants.MAP

  /** One record for [path]: an `entry` with a `key` and a `value` in a map, an `option` with a `value` elsewhere. */
  fun newRecord(xmlDoc: Document, path: Path, trusted: Boolean): Element =
    if (holdsStatePerPath) {
      xmlDoc.createElement(Constants.ENTRY).apply {
        setAttribute(Constants.KEY, "$path")
        setAttribute(Constants.VALUE, "$trusted")
      }
    }
    else {
      xmlDoc.createElement(Constants.OPTION).apply { setAttribute(Constants.VALUE, "$path") }
    }
}

/**
 * The storage the trust state of one project goes to. Rider uses the trusted paths setting, because the starter
 * cannot build the exact string its own `TrustedSolutionStore` matches. So a Rider record trusts the whole subtree.
 */
private fun IDETestContext.projectStateStorage(configPath: Path): TrustStorage =
  if (isRider) trustedPathSettingStorage(configPath) else trustedPathsStorage(configPath)

/** Every storage that resolves the trust state of a path, so [ProjectTrustState.ASK] can clear them all. */
private fun IDETestContext.allTrustStorages(configPath: Path): List<TrustStorage> =
  if (isRider) listOf(trustedPathSettingStorage(configPath), riderSolutionStorage(configPath))
  else listOf(trustedPathsStorage(configPath), trustedPathSettingStorage(configPath))

/** The storage of the trust state of one project, for every product except Rider. */
private fun trustedPathsStorage(configPath: Path): TrustStorage =
  trustStorageOf(configPath, TrustedPaths::class, TrustedPaths.State::trustedPaths)

/** The storage of the trusted paths setting. It holds for every product, Rider included. */
private fun trustedPathSettingStorage(configPath: Path): TrustStorage =
  trustStorageOf(configPath, TrustedPathsSettings::class, TrustedPathsSettings.State::trustedPaths)

/**
 * The storage of the trusted solutions of Rider. Only [ProjectTrustState.ASK] needs it. Every name is a literal,
 * because `TrustedSolutionStore` lives in `rider/`.
 */
private fun riderSolutionStorage(configPath: Path): TrustStorage = TrustStorage(
  file = optionsFile(configPath, "trustedSolutions.xml"),
  component = TRUSTED_SOLUTION_STORE,
  option = "trustedLocations",
  containerTag = Constants.SET,
)

/**
 * Reads where the platform keeps [stateClass]: the file of its `@Storage`, the name of its `@State`, the serialized
 * name of the state property [records], and the tag [records] gets. A rename in the platform breaks this build.
 */
private fun trustStorageOf(configPath: Path, stateClass: KClass<*>, records: KProperty1<*, *>): TrustStorage {
  val state = requireNotNull(stateClass.java.getAnnotation(State::class.java)) { "${stateClass.simpleName} has no @State" }
  val storage = requireNotNull(state.storages.firstOrNull()) { "${state.name} declares no @Storage" }
  val optionTag = requireNotNull(records.javaField?.getAnnotation(OptionTag::class.java)) {
    "${state.name}.${records.name} has no @OptionTag"
  }
  // `com.intellij.util.xmlb` names the container after the property type, unless `@XCollection` overrides it
  val containerTag = when (records.returnType.classifier) {
    Map::class -> Constants.MAP
    List::class -> Constants.LIST
    Set::class -> Constants.SET
    else -> error("${state.name}.${records.name} is a ${records.returnType}. The serializer names no container for it.")
  }
  return TrustStorage(optionsFile(configPath, storage.value), state.name, optionTag.value, containerTag)
}

/** The config file [fileName] of an application level component. */
private fun optionsFile(configPath: Path, fileName: String): Path =
  configPath.toAbsolutePath().resolve(PathManager.OPTIONS_DIRECTORY).resolve(fileName)

private val IDETestContext.isRider: Boolean
  get() = ide.productCode == IdeInfoType.RIDER.productCode

/** The root of a config file of the application level. The starter creates it when the file does not exist. */
private const val APPLICATION_TAG = "application"
private const val TRUSTED_SOLUTION_STORE = "TrustedSolutionStore"
private const val TRUST_EVERYTHING_OPTION = "trustEverything"
