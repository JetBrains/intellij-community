package com.intellij.mcpserver.elicitation


// ---- DSL -----------------------------------------------------------------
/** Scope marker for the elicitation form builder DSL. */
@DslMarker
annotation class ElicitationDsl

/**
 * Builder for an [ElicitationForm]. Set [message] and add fields via the typed
 * `stringField`/`integerField`/`booleanField`/`singleSelect`/... methods.
 */
@ElicitationDsl
class ElicitationFormBuilder @PublishedApi internal constructor() {
  var message: String = ""
  private val fields = mutableListOf<ElicitationField>()

  fun stringField(name: String, block: StringFieldBuilder.() -> Unit = {}) {
    fields += StringFieldBuilder(name).apply(block).build()
  }

  fun integerField(name: String, block: IntegerFieldBuilder.() -> Unit = {}) {
    fields += IntegerFieldBuilder(name).apply(block).build()
  }

  fun numberField(name: String, block: NumberFieldBuilder.() -> Unit = {}) {
    fields += NumberFieldBuilder(name).apply(block).build()
  }

  fun booleanField(name: String, block: BooleanFieldBuilder.() -> Unit = {}) {
    fields += BooleanFieldBuilder(name).apply(block).build()
  }

  fun singleSelect(name: String, block: SingleSelectFieldBuilder.() -> Unit) {
    fields += SingleSelectFieldBuilder(name).apply(block).build()
  }

  fun multiSelect(name: String, block: MultiSelectFieldBuilder.() -> Unit) {
    fields += MultiSelectFieldBuilder(name).apply(block).build()
  }

  @PublishedApi
  internal fun build(): ElicitationForm {
    require(message.isNotEmpty()) { "Elicitation form message is required" }
    return ElicitationForm(message, fields.toList())
  }
}

@ElicitationDsl
class StringFieldBuilder internal constructor(private val name: String) {
  var title: String? = null
  var description: String? = null
  var required: Boolean = false
  var minLength: Int? = null
  var maxLength: Int? = null
  var format: StringFieldFormat? = null
  var default: String? = null

  internal fun build(): ElicitationField =
    StringField(name, title, description, required, minLength, maxLength, format, default)
}

@ElicitationDsl
class IntegerFieldBuilder internal constructor(private val name: String) {
  var title: String? = null
  var description: String? = null
  var required: Boolean = false
  var minimum: Int? = null
  var maximum: Int? = null
  var default: Int? = null

  internal fun build(): ElicitationField =
    IntegerField(name, title, description, required, minimum, maximum, default)
}

@ElicitationDsl
class NumberFieldBuilder internal constructor(private val name: String) {
  var title: String? = null
  var description: String? = null
  var required: Boolean = false
  var minimum: Double? = null
  var maximum: Double? = null
  var default: Double? = null

  internal fun build(): ElicitationField =
    NumberField(name, title, description, required, minimum, maximum, default)
}

@ElicitationDsl
class BooleanFieldBuilder internal constructor(private val name: String) {
  var title: String? = null
  var description: String? = null
  var required: Boolean = false
  var default: Boolean? = null

  internal fun build(): ElicitationField =
    BooleanField(name, title, description, required, default)
}

@ElicitationDsl
class SingleSelectFieldBuilder internal constructor(private val name: String) {
  var title: String? = null
  var description: String? = null
  var required: Boolean = false
  var options: List<EnumOption> = emptyList()
  var default: String? = null

  fun option(value: String, title: String? = null) {
    options = options + EnumOption(value, title)
  }

  internal fun build(): ElicitationField {
    require(options.isNotEmpty()) { "Single-select field '$name' must have at least one option" }
    return SingleSelectField(name, title, description, required, options, default)
  }
}

@ElicitationDsl
class MultiSelectFieldBuilder internal constructor(private val name: String) {
  var title: String? = null
  var description: String? = null
  var required: Boolean = false
  var options: List<EnumOption> = emptyList()
  var minItems: Int? = null
  var maxItems: Int? = null
  var default: List<String>? = null

  fun option(value: String, title: String? = null) {
    options = options + EnumOption(value, title)
  }

  internal fun build(): ElicitationField {
    require(options.isNotEmpty()) { "Multi-select field '$name' must have at least one option" }
    return MultiSelectField(name, title, description, required, options, minItems, maxItems, default)
  }
}

/** Builds an [ElicitationForm] using the DSL [block]. */
@PublishedApi
internal fun buildElicitationForm(block: ElicitationFormBuilder.() -> Unit): ElicitationForm =
  ElicitationFormBuilder().apply(block).build()

/** Format hint for a string field, mapped to the MCP string schema `format`. */
enum class StringFieldFormat { Email, Uri, Date, DateTime }

/** One choice in a single- or multi-select field: a [value] plus optional display [title]. */
data class EnumOption(val value: String, val title: String? = null)

internal data class StringField(
  override val name: String,
  override val title: String? = null,
  override val description: String? = null,
  override val required: Boolean = false,
  val minLength: Int? = null,
  val maxLength: Int? = null,
  val format: StringFieldFormat? = null,
  val default: String? = null,
) : ElicitationField
internal data class IntegerField(
  override val name: String,
  override val title: String? = null,
  override val description: String? = null,
  override val required: Boolean = false,
  val minimum: Int? = null,
  val maximum: Int? = null,
  val default: Int? = null,
) : ElicitationField

internal data class NumberField(
  override val name: String,
  override val title: String? = null,
  override val description: String? = null,
  override val required: Boolean = false,
  val minimum: Double? = null,
  val maximum: Double? = null,
  val default: Double? = null,
) : ElicitationField

internal data class BooleanField(
  override val name: String,
  override val title: String? = null,
  override val description: String? = null,
  override val required: Boolean = false,
  val default: Boolean? = null,
) : ElicitationField

internal data class SingleSelectField(
  override val name: String,
  override val title: String? = null,
  override val description: String? = null,
  override val required: Boolean = false,
  val options: List<EnumOption>,
  val default: String? = null,
) : ElicitationField

internal data class MultiSelectField(
  override val name: String,
  override val title: String? = null,
  override val description: String? = null,
  override val required: Boolean = false,
  val options: List<EnumOption>,
  val minItems: Int? = null,
  val maxItems: Int? = null,
  val default: List<String>? = null,
) : ElicitationField
