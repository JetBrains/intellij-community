use pretty_assertions::assert_eq;

use super::*;

fn read_text(text: &str) -> Source {
    read(text, &|key| key == "suite-a" || key == "suite-b")
}

fn paths(source: &Source) -> Vec<&str> {
    source.declarations.iter().map(|declaration| declaration.path.as_str()).collect()
}

fn declaration<'s>(source: &'s Source, path: &str) -> &'s Declaration {
    &source.declarations[source
        .find(path)
        .unwrap_or_else(|| panic!("no declaration {path} in {:?}", paths(source)))]
}

fn references(source: &Source, path: &str) -> Vec<String> {
    declaration(source, path).references.iter().cloned().collect()
}

/// Each member of a class body is a declaration of its own, the body of a function belongs to the function, and a
/// companion object without a name is `Companion`.
#[test]
fn a_file_splits_into_its_declarations_and_the_members_of_each_body() {
    let source = read_text(
        r#"package a.b

import a.c.Helper

/** A driver. */
@Suppress("unused")
internal class Driver(private val bridges: Bridges) : Base(), Probe {
  init {
    Helper.start()
  }

  suspend fun open(path: String) {
    val local = object : Listener {
      override fun fired() = Unit
    }
    fun nested() = 1
    click(path)
  }

  private val timeout: Duration
    get() = 2.seconds

  private companion object {
    const val ID: String = "id"
  }
}

fun String.extension(): Int = length

typealias Callback = () -> Unit
"#,
    );
    assert_eq!(
        paths(&source),
        [
            "Driver",
            "Driver.open",
            "Driver.timeout",
            "Driver.Companion",
            "Driver.Companion.ID",
            "extension",
            "Callback"
        ]
    );
    let driver = declaration(&source, "Driver");
    assert_eq!(
        (driver.kind, driver.supertypes.clone()),
        (Kind::Class, vec!["Base".to_owned(), "Probe".to_owned()])
    );
    // The init block is a part of its class.
    assert!(driver.references.contains("start"));
    assert!(declaration(&source, "Driver.Companion").companion);
    assert!(declaration(&source, "Driver.timeout").private);
    assert_eq!(source.imports, BTreeSet::from(["a.c.Helper".to_owned()]));
}

/// The token that binds a parameter, a local, a loop variable, a lambda parameter or a named argument names nothing of
/// another place, and neither does the own name. A later use of a bound name stays a reference, because it can also
/// name a declaration of another place.
#[test]
fn the_references_leave_out_only_the_tokens_that_bind_a_name() {
    let source = read_text(
        r"
fun open(path: String, count: Int = DEFAULT) {
  val state = read(path)
  items.forEach { item -> use(item) }
  for (entry in entries) show(entry)
  send(timeout = state, other = bridge.state)
  open(path)
}
",
    );
    assert_eq!(
        references(&source, "open"),
        [
            "DEFAULT", "Int", "String", "bridge", "entries", "entry", "forEach", "item", "items", "open", "path", "read", "send", "show",
            "state", "use"
        ]
    );
}

/// A type after `:`, `is` or `as`, a name after a dot, and the first branch of a `when` bind nothing, so each one stays
/// a reference. A lambda parameter does not hide a declaration of the same name.
#[test]
fn a_type_a_when_branch_and_a_used_lambda_parameter_stay_references() {
    let source = read_text(
        r"
fun typed() = plans.map { plan: Plan -> Plan.helper(plan) }

fun subject(mode: Mode) = when (mode) {
  is Fast -> fast()
  SLOW, IDLE -> slow()
  else -> other(mode as Custom)
}

fun bare() = when {
  ready -> go()
  else -> wait()
}

fun parsed() = texts.map { json -> json.parse() }

fun first(mode: Mode) = when (mode) {
  FAST, SLOW -> quick()
  else -> slow()
}
",
    );
    for (path, wanted) in [
        ("typed", vec!["Plan", "helper"]),
        ("subject", vec!["Fast", "SLOW", "IDLE", "Custom"]),
        ("bare", vec!["ready"]),
        ("parsed", vec!["json"]),
        ("first", vec!["FAST", "SLOW"]),
    ] {
        let found = references(&source, path);
        for name in wanted {
            assert!(found.contains(&name.to_owned()), "{path} lost {name}: {found:?}");
        }
    }
}

/// A property with a getter on its own line is named by its name, a semicolon ends a declaration as a line break does,
/// and a context parameter list leads the declaration that follows it.
#[test]
fn a_one_line_getter_a_semicolon_and_a_context_list_split_as_the_source_does() {
    let source = read_text(
        r"
class A; class B

val label get() = compute()

fun before() = 1

context(scope: Scope)
fun after() = scope.run()
",
    );
    assert_eq!(paths(&source), ["A", "B", "label", "before", "after"]);
    assert_eq!(references(&source, "before"), Vec::<String>::new());
    assert!(references(&source, "after").contains(&"Scope".to_owned()));
}

/// The value of a pair keyed by a suite is created with its map, so every user of the map reaches what it names. The
/// pair still has its own fingerprint.
#[test]
fn the_value_of_a_suite_keyed_pair_is_a_reference_of_its_map() {
    let source = read_text("val parts = mapOf(\n  \"suite-a/scenario/part\" to Shared.compute(),\n)\n");
    let parts = declaration(&source, "parts");
    assert!(parts.references.contains("compute"), "{:?}", parts.references);
    assert!(parts.entries.contains_key("suite-a"));
}

/// The package line and the imports are outside every declaration, and the fingerprint of the file holds them.
#[test]
fn the_fingerprint_of_a_file_holds_its_package_line() {
    let one = read_text("package a\n\nfun open() = 1\n");
    let two = read_text("package b\n\nfun open() = 1\n");
    assert_eq!(declaration(&one, "open").fingerprint, declaration(&two, "open").fingerprint);
    assert!(one.fingerprint != two.fingerprint);
}

/// A comment and a KDoc are no tokens, so a change of one changes no fingerprint. A change of a token does.
#[test]
fn a_comment_changes_no_fingerprint_and_a_token_does() {
    let before = read_text("fun open() {\n  click(1)\n}\n");
    let commented = read_text("/** Opens. */\nfun open() {\n  // the one click\n  click(1) /* first */\n}\n");
    let changed = read_text("fun open() {\n  click(2)\n}\n");
    let fingerprint = |source: &Source| declaration(source, "open").fingerprint;
    assert_eq!(fingerprint(&before), fingerprint(&commented));
    assert!(fingerprint(&before) != fingerprint(&changed));
}

/// A branch keyed by a suite and a pair keyed by a suite are entries of that suite, with their own names and
/// fingerprints. The comma after a pair belongs to the pair, so a new pair leaves the rest of the declaration as it
/// was. A key that names no suite stays a part of the declaration.
#[test]
fn a_suite_keyed_entry_is_no_part_of_its_declaration() {
    let one = read_text(
        r#"
object Bodies {
  val parts = linkedMapOf(
    "suite-a/scenario/part" to Contract(
      takes = emptyMap(),
    ),
  )

  fun run(key: String) = when (key) {
    "suite-a/scenario/part" -> {
      with(BodyA) { part() }
    }
    "action/other" -> other()
    else -> error(key)
  }
}
"#,
    );
    let two = read_text(
        r#"
object Bodies {
  val parts = linkedMapOf(
    "suite-a/scenario/part" to Contract(
      takes = emptyMap(),
    ),
    "suite-b/scenario/part" to Contract(
      takes = emptyMap(),
    ),
  )

  fun run(key: String) = when (key) {
    "suite-a/scenario/part" -> {
      with(BodyA) { part() }
    }
    "suite-b/scenario/part" -> {
      with(BodyB) { part() }
    }
    "action/other" -> other()
    else -> error(key)
  }
}
"#,
    );
    for path in ["Bodies.parts", "Bodies.run"] {
        assert_eq!(declaration(&one, path).fingerprint, declaration(&two, path).fingerprint, "{path}");
        assert_eq!(
            declaration(&one, path).entries["suite-a"],
            declaration(&two, path).entries["suite-a"],
            "{path}"
        );
    }
    let run = declaration(&two, "Bodies.run");
    assert_eq!(run.entries.keys().collect::<Vec<_>>(), ["suite-a", "suite-b"]);
    assert!(run.entries["suite-b"].references.contains("BodyB"));
    assert!(!run.references.contains("BodyA"));
    // The key `action` names no suite.
    assert!(run.references.contains("other"));
}

/// The `@SerialName` of a type, its modifiers, and whether a property runs code when its type is created.
#[test]
fn a_declaration_carries_its_serial_name_its_modifiers_and_its_creation_code() {
    let source = read_text(
        r#"
@Serializable
@SerialName("action/open")
data class Open(override val timeoutMs: Long = 1) : Request<Unit> {
  override fun run(bridges: Bridges) = bridges.answer(Handlers.open())

  operator fun invoke() = Unit
}

class Holder {
  val plain: String = "text"
  val computed: Int get() = compute()
  val created = Scope()
  val lazy by lazy { 1 }
}
"#,
    );
    let open = declaration(&source, "Open");
    assert_eq!(open.serial_name.as_deref(), Some("action/open"));
    assert_eq!(open.supertypes, ["Request"]);
    assert!(declaration(&source, "Open.run").overrides);
    assert!(declaration(&source, "Open.invoke").operator);
    let runs = |path: &str| declaration(&source, path).runs_on_creation;
    assert_eq!(
        [
            runs("Holder.plain"),
            runs("Holder.computed"),
            runs("Holder.created"),
            runs("Holder.lazy")
        ],
        [false, false, true, true]
    );
}

/// An anonymous object or function in an expression body on its own line starts no declaration.
#[test]
fn an_anonymous_object_on_its_own_line_is_a_part_of_its_declaration() {
    let source = read_text(
        r"
val listener =
  object : Listener {
    override fun fired() = Unit
  }

val callback =
  fun(value: Int) = value
",
    );
    assert_eq!(paths(&source), ["listener", "callback"]);
    assert!(references(&source, "listener").contains(&"Listener".to_owned()));
}

/// A string template is lexed, so a name in it is a reference. The text of a literal is not.
#[test]
fn a_template_of_a_string_is_a_reference_and_its_text_is_not() {
    let source = read_text("fun describe() = \"open ${target.name} with $mode \\\"quoted\\\" and more\"\n");
    assert_eq!(references(&source, "describe"), ["mode", "name", "target"]);
}

/// The name that an import line brings in is its alias, or its last segment.
#[test]
fn an_import_brings_in_its_alias_or_its_last_segment() {
    assert_eq!(imported_name("a.b.Helper"), "Helper");
    assert_eq!(imported_name("a.b.Helper as Other"), "Other");
}
