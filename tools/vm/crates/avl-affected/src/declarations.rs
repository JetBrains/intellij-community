//! The declaration relation: a changed Kotlin file of a harness module reaches the suites whose requests reach a
//! declaration that the change changed.
//!
//! # Why this exists
//!
//! The test bridge holds the drivers of every suite, so a path of its module was the harness of every lane. One
//! changed bridge function selected 86 suites in three lanes on 2026-10-27, and a commit that only added the driver
//! functions of one new suite selected every UI suite. Most bridge declarations serve a few suites. The lane table
//! names the module in `declarationReach`, and this relation answers for its Kotlin sources.
//!
//! # The reach
//!
//! The relation reads every declaration of the sources with [`kotlin::read`], and links each one to the declarations
//! that it names. A name links every declaration of that name, because the reader knows no types. A private
//! declaration is linked only from its own file. A member of a class or an interface is reached only when its type is
//! reached too: the rapid type analysis of a call graph, which keeps a common member name such as `run` from linking
//! every type that declares one. A reached type also reaches its supertypes, the properties that run code when it is
//! created, its operator functions, and each override that no supertype of the sources declares, because the platform
//! calls those. A member also reaches each override of it in a subtype, behind the same type gate. A sealed type
//! reaches its subtypes, because a decoder creates them without a name. The sealed request type is the exception.
//!
//! A name that a declaration binds itself, such as a parameter or a lambda parameter, is no reference. A later use of
//! that name stays one: it can also name a declaration of another place, and a reference too many costs a suite, never
//! a missed one. The value of a suite-keyed pair is created with its map, so every user of the map reaches it, and a
//! changed pair that runs a function or a property of the sources changes the map for every user.
//!
//! The roots of a suite are its requests. The guest sends each instruction that runs inside the IDE as the request
//! whose `@SerialName` is `action/<id>` or `assertion/<id>`, so the suite document names its requests in its
//! vocabulary. An authored suite runs its parts through the suite-keyed entries of the dispatcher, so those entries are
//! roots of their suite too.
//!
//! The roots of every suite are the classes that the IDE creates by itself, which the module descriptors name, and
//! every other request type: the state reset, the readiness probe and the requests that a suite document holds as raw
//! JSON. A declaration that these roots reach is reached by every suite, and it stays lane-wide.
//!
//! # The changed declarations
//!
//! The caller names paths, not declarations. So the relation compares each named file with its text at the baseline
//! ([`Baseline`]), declaration by declaration, by the fingerprint of the tokens. An added or changed declaration is
//! changed. A removed one changes its type and every declaration that names it. An added, removed or changed import
//! changes every declaration of the file that names the imported name. When the tokens of the file differ and no
//! declaration does, such as for a new package line, the whole file counts as changed. A change of comments and spaces
//! alone changes nothing.
//!
//! # Where it stays conservative
//!
//! - A declaration that every suite reaches makes the path lane-wide, as before.
//! - A suite whose document names a request that no request type of the sources declares, or an authored suite
//!   without an entry, reaches every changed declaration.
//! - A file without a baseline text, because the checkout cannot read one, counts as changed as a whole. So does a
//!   file whose text equals its baseline: the caller asks about the whole file.
//! - A source that cannot be listed or read, a descriptor that cannot be read, a file of the module that is no
//!   Kotlin source, or a file that the checkout no longer holds keeps the lane-wide answer.
//!
//! # The vocabulary file
//!
//! The module serves the flow vocabulary, whose entries name actions, assertions and authored suites. A changed entry
//! reaches the suites whose vocabulary holds it. A change outside these three sections is lane-wide.

pub(crate) mod kotlin;

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
use std::sync::OnceLock;

use bt_core::areas::Area;
use bt_core::catalog::{SuiteDocument, read_suite_documents};
use bt_core::lanes::DeclarationReach;
use bt_core::runtime::{Runtime, par_map, repo_file};
use bt_core::scan::walk_dirs;
use serde_json::Value;

use crate::regex;
use kotlin::{Kind, Source};

/// The prefix an [`bt_core::AffectedSuite::via`] entry of the declaration relation carries. The value is the path of
/// the changed declaration in its file, such as `AirUiTestBridge.openProject`, or the file name when the whole file
/// counts as changed.
pub const VIA_DECLARATION: &str = "declaration:";

/// The prefix an [`bt_core::AffectedSuite::via`] entry of a changed vocabulary entry carries, such as
/// `vocabulary:actions/open-project`.
pub const VIA_VOCABULARY: &str = "vocabulary:";

/// What a named file is compared with to find its changed declarations.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Baseline<'a> {
    /// The working tree against the commit where `HEAD` meets its upstream: every change that is not pushed yet. What
    /// a caller has after it edited and committed in its own checkout.
    Upstream,
    /// The working tree against this revision. A CI build names the first parent of each commit, `<commit>^`, so a
    /// later commit of the build that changed the same file adds its declarations too.
    Revision(&'a str),
}

/// What the requests of one suite are, read from its document.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct SuiteRequests {
    /// The `@SerialName` of each request that the suite sends: `action/<id>` and `assertion/<id>` of each vocabulary
    /// entry that runs inside the IDE.
    pub requests: Vec<String>,
    /// Each vocabulary entry as `actions/<id>` or `assertions/<id>`, and `authored/<suite>` for an authored suite.
    pub vocabulary: BTreeSet<String>,
    pub authored: bool,
}

impl SuiteRequests {
    /// The requests of the suite of `document`, or `None` when its document cannot be read again.
    pub(crate) fn read(runtime: &dyn Runtime, document: &SuiteDocument) -> Option<Self> {
        let text = runtime.read_text_file(&repo_file(runtime, &document.path)).ok()?;
        let value: Value = serde_json::from_str(&text).ok()?;
        let mut requests = Self {
            authored: value.get("authored").and_then(Value::as_bool).unwrap_or(false),
            ..Self::default()
        };
        for (section, kind) in [("actions", "action"), ("assertions", "assertion")] {
            let Some(entries) = value.pointer(&format!("/vocabulary/{section}")).and_then(Value::as_object) else {
                continue;
            };
            for (id, entry) in entries {
                requests.vocabulary.insert(format!("{section}/{id}"));
                if entry.get("side").and_then(Value::as_str) != Some("host") {
                    requests.requests.push(format!("{kind}/{id}"));
                }
            }
        }
        if requests.authored {
            requests.vocabulary.insert(format!("authored/{}", document.suite));
        }
        Some(requests)
    }
}

/// The text of a named file at the baseline.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum BaselineText<'t> {
    Text(&'t str),
    /// The baseline holds no such file.
    Absent,
    /// The checkout cannot read the baseline.
    Unreadable,
}

/// What one changed path of the module reaches.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Reach {
    /// Every suite of the lanes that the module serves.
    LaneWide,
    /// These suites, by document index, each with its `via` entries.
    Suites(BTreeMap<usize, BTreeSet<String>>),
}

/// The declarations of the sources of one harness module, and what each suite reaches.
pub(crate) struct DeclarationIndex {
    files: Vec<String>,
    sources: Vec<Source>,
    /// The file and the declaration of each node.
    nodes: Vec<(usize, usize)>,
    /// The node of each declaration, by file.
    node_of: Vec<Vec<usize>>,
    successors: Vec<Vec<usize>>,
    /// Whether a node is a member of a class or an interface: it is reached only after its type.
    gated: Vec<Option<usize>>,
    /// The roots of each suite, by document index, or `None` for a suite with a request that no request type declares.
    suite_roots: Vec<Option<Vec<usize>>>,
    /// The functions that hold a suite-keyed entry.
    dispatchers: Vec<usize>,
    /// Whether a suite has a suite-keyed entry or no authored part, by document index.
    keyed: Vec<bool>,
    /// What the roots of every suite reach.
    global: Walk,
    suite_reach: Vec<OnceLock<Option<Vec<bool>>>>,
    suite_ids: Vec<String>,
    vocabularies: Vec<BTreeSet<String>>,
    /// The name of every function and property of the sources: what a pair of a map can run when the map is created.
    runnable_names: HashSet<String>,
}

impl DeclarationIndex {
    /// Reads the sources of `reach`. An error is a source or a descriptor that cannot be read, and the caller keeps
    /// the lane-wide answer for it.
    pub(crate) fn build(
        runtime: &dyn Runtime,
        reach: &DeclarationReach,
        documents: &[SuiteDocument],
        suites: &[Option<SuiteRequests>],
    ) -> Result<Self, String> {
        let suite_ids: HashSet<&str> = documents.iter().map(|document| document.suite.as_str()).collect();
        let is_suite = |key: &str| suite_ids.contains(key);
        // A walk and not the version control: a test reads the sources from its runfiles, which hold no checkout.
        for directory in &reach.sources {
            runtime
                .read_dir(&repo_file(runtime, directory))
                .map_err(|error| format!("cannot list {directory}: {error}"))?;
        }
        let mut files = Vec::new();
        walk_dirs(runtime, reach.sources.clone(), |directory, entries, next| {
            for entry in entries {
                let child = format!("{directory}/{}", entry.name);
                if entry.is_dir {
                    next.push(child);
                } else if entry.name.ends_with(".kt") {
                    files.push(child);
                }
            }
        });
        files.sort();
        files.dedup();
        let texts = par_map(&files, |file| runtime.read_text_file(&repo_file(runtime, file)));
        let mut sources = Vec::with_capacity(files.len());
        for (file, text) in files.iter().zip(texts) {
            let text = text.map_err(|error| format!("cannot read {file}: {error}"))?;
            sources.push(kotlin::read(&text, &is_suite));
        }
        let mut created = BTreeSet::new();
        for descriptor in &reach.descriptors {
            let text = runtime
                .read_text_file(&repo_file(runtime, descriptor))
                .map_err(|error| format!("cannot read {descriptor}: {error}"))?;
            for found in regex!(r#""([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+)""#).captures_iter(&text) {
                let class = &found[1];
                created.insert(class.rsplit(['.', '$']).next().unwrap_or(class).to_owned());
            }
        }
        // The suites with authored parts run them through the suite-keyed entries. Without the vocabulary, the document
        // says whether a suite is authored.
        let authored_parts = reach.vocabulary.as_ref().and_then(|vocabulary| {
            let text = runtime.read_text_file(&repo_file(runtime, vocabulary)).ok()?;
            let value: Value = serde_json::from_str(&text).ok()?;
            let parts = value.get("authored")?.as_object()?;
            Some(
                parts
                    .iter()
                    .filter(|(_, parts)| parts.as_array().is_some_and(|parts| !parts.is_empty()))
                    .map(|(suite, _)| suite.clone())
                    .collect::<HashSet<String>>(),
            )
        });
        Ok(Self::link(
            files,
            sources,
            &reach.request_type,
            &created,
            documents,
            suites,
            authored_parts.as_ref(),
        ))
    }

    fn link(
        files: Vec<String>,
        sources: Vec<Source>,
        request_type: &str,
        created: &BTreeSet<String>,
        documents: &[SuiteDocument],
        suites: &[Option<SuiteRequests>],
        authored_parts: Option<&HashSet<String>>,
    ) -> Self {
        let mut nodes = Vec::new();
        let mut node_of = Vec::with_capacity(sources.len());
        for (file, source) in sources.iter().enumerate() {
            let mut of_file = Vec::with_capacity(source.declarations.len());
            for declaration in 0..source.declarations.len() {
                of_file.push(nodes.len());
                nodes.push((file, declaration));
            }
            node_of.push(of_file);
        }
        let (successors, gated, suite_roots, global_roots, dispatchers, keyed) = {
            let declaration = |node: usize| {
                let (file, index) = nodes[node];
                &sources[file].declarations[index]
            };
            let container = |node: usize| {
                let (file, _) = nodes[node];
                declaration(node).container.map(|index| node_of[file][index])
            };
            let mut types_by_name: HashMap<&str, Vec<usize>> = HashMap::new();
            for node in 0..nodes.len() {
                if declaration(node).kind.is_type() {
                    types_by_name.entry(&declaration(node).name).or_default().push(node);
                }
            }
            // Every type whose supertypes reach the request type is a request type.
            let mut request_types: HashSet<&str> = HashSet::from([request_type]);
            loop {
                let before = request_types.len();
                for node in 0..nodes.len() {
                    let found = declaration(node);
                    if found.kind.is_type() && found.supertypes.iter().any(|supertype| request_types.contains(supertype.as_str())) {
                        request_types.insert(&found.name);
                    }
                }
                if request_types.len() == before {
                    break;
                }
            }
            let is_request = |node: usize| {
                let found = declaration(node);
                found.kind.is_type() && found.kind != Kind::Interface && request_types.contains(found.name.as_str())
            };
            let is_flow_request = |node: usize| {
                is_request(node)
                    && declaration(node)
                        .serial_name
                        .as_deref()
                        .is_some_and(|name| name.starts_with("action/") || name.starts_with("assertion/"))
            };
            // A member of a request runs when the server decodes the request, never by its name. A flow request is
            // created only by that decoding, for the suites that send it, so its name in a signature reaches nothing.
            let dispatched: Vec<bool> = (0..nodes.len()).map(|node| container(node).is_some_and(is_request)).collect();
            let mut by_name: HashMap<String, Vec<usize>> = HashMap::new();
            for (node, &dispatched) in dispatched.iter().enumerate() {
                if !dispatched && !is_flow_request(node) {
                    by_name.entry(declaration(node).name.clone()).or_default().push(node);
                }
            }
            let visible = |target: usize, from_file: usize| !declaration(target).private || nodes[target].0 == from_file;
            let mut members: Vec<Vec<usize>> = vec![Vec::new(); nodes.len()];
            for node in 0..nodes.len() {
                if let Some(owner) = container(node) {
                    members[owner].push(node);
                }
            }
            let supertypes_of = |node: usize| -> Vec<usize> {
                declaration(node)
                    .supertypes
                    .iter()
                    .flat_map(|name| types_by_name.get(name.as_str()).into_iter().flatten().copied())
                    .filter(|&supertype| supertype != node && visible(supertype, nodes[node].0))
                    .collect()
            };
            // An import of an object member, `a.Holder.member` or `a.Holder.*`, lets the file call the member without the
            // name of the object, so a reference to the member also reaches the object.
            let imported_holders: Vec<HashMap<&str, Vec<usize>>> = sources
                .iter()
                .map(|source| {
                    let mut holders: HashMap<&str, Vec<usize>> = HashMap::new();
                    for import in &source.imports {
                        let mut segments = import.split(" as ").next().unwrap_or(import).rsplit('.');
                        let (Some(member), Some(holder)) = (segments.next(), segments.next()) else {
                            continue;
                        };
                        let objects = types_by_name.get(holder).into_iter().flatten().copied();
                        for object in objects.filter(|&object| declaration(object).kind == Kind::Object) {
                            if member == "*" {
                                for &inner in &members[object] {
                                    holders.entry(declaration(inner).name.as_str()).or_default().push(object);
                                }
                            } else {
                                holders.entry(kotlin::imported_name(import)).or_default().push(object);
                            }
                        }
                    }
                    holders
                })
                .collect();
            let named = |name: &str, from_file: usize| -> Vec<usize> {
                let targets = by_name.get(name).into_iter().flatten().copied();
                let holders = imported_holders[from_file].get(name).into_iter().flatten().copied();
                targets.filter(|&target| visible(target, from_file)).chain(holders).collect()
            };
            // The first use of a top-level declaration initializes every top-level property of its file.
            let mut file_initializers: Vec<Vec<usize>> = vec![Vec::new(); sources.len()];
            for node in 0..nodes.len() {
                if declaration(node).container.is_none() && declaration(node).runs_on_creation {
                    file_initializers[nodes[node].0].push(node);
                }
            }
            // The subtypes of each type in the sources, through every level.
            let mut subtypes: Vec<Vec<usize>> = vec![Vec::new(); nodes.len()];
            for node in 0..nodes.len() {
                if declaration(node).kind.is_type() {
                    let mut seen = HashSet::from([node]);
                    let mut stack = supertypes_of(node);
                    while let Some(supertype) = stack.pop() {
                        if seen.insert(supertype) {
                            subtypes[supertype].push(node);
                            stack.extend(supertypes_of(supertype));
                        }
                    }
                }
            }
            let mut successors: Vec<Vec<usize>> = Vec::with_capacity(nodes.len());
            for node in 0..nodes.len() {
                let found = declaration(node);
                let file = nodes[node].0;
                let mut next: Vec<usize> = found.references.iter().flat_map(|name| named(name, file)).collect();
                if found.container.is_none() && !found.kind.is_type() {
                    next.extend(&file_initializers[file]);
                }
                if found.kind.is_type() {
                    let supertypes = supertypes_of(node);
                    let inherited = inherited_names(node, &supertypes_of, &members, &declaration);
                    for &member in &members[node] {
                        let member_declaration = declaration(member);
                        let platform_override = member_declaration.overrides && !inherited.contains(member_declaration.name.as_str());
                        if member_declaration.runs_on_creation
                            || member_declaration.operator
                            || member_declaration.companion
                            || dispatched[member]
                            || platform_override
                        {
                            next.push(member);
                        }
                    }
                    next.extend(supertypes);
                    // A sealed type is decoded into one of its subtypes, which no code needs to name. A request type is
                    // the exception: a suite sends its own requests.
                    if found.sealed && !request_types.contains(found.name.as_str()) {
                        next.extend(&subtypes[node]);
                    }
                }
                // A member reaches each override of it in a subtype, which a call through the supertype runs. The type
                // gate still waits for the subtype, so only a created subtype answers.
                if let Some(owner) = container(node)
                    && !found.kind.is_type()
                {
                    for &subtype in &subtypes[owner] {
                        next.extend(
                            members[subtype]
                                .iter()
                                .copied()
                                .filter(|&member| declaration(member).overrides && declaration(member).name == found.name),
                        );
                    }
                }
                next.sort_unstable();
                next.dedup();
                successors.push(next);
            }
            // A member is reached only after its type. A member of a companion object waits for the class around the
            // companion instead, because a call names that class or no type at all.
            let gated: Vec<Option<usize>> = (0..nodes.len())
                .map(|node| {
                    let owner = container(node).filter(|_| !declaration(node).kind.is_type())?;
                    if declaration(owner).companion {
                        return container(owner).or(Some(owner));
                    }
                    Some(owner)
                })
                .collect();
            let mut global_roots = Vec::new();
            let mut by_serial_name: HashMap<&str, usize> = HashMap::new();
            for node in 0..nodes.len() {
                let found = declaration(node);
                if found.kind.is_type() && created.contains(&found.name) {
                    global_roots.push(node);
                }
                if found.kind == Kind::Function && found.operator && found.container.is_none() {
                    global_roots.push(node);
                }
                if is_flow_request(node) {
                    by_serial_name.insert(found.serial_name.as_deref().unwrap_or_default(), node);
                } else if is_request(node) {
                    global_roots.push(node);
                }
            }
            let mut entries_of: HashMap<&str, Vec<usize>> = HashMap::new();
            for (node, &(file, _)) in nodes.iter().enumerate() {
                for (suite, entry) in &declaration(node).entries {
                    let roots = entries_of.entry(suite.as_str()).or_default();
                    roots.extend(entry.references.iter().flat_map(|name| named(name, file)));
                }
            }
            let suite_roots: Vec<Option<Vec<usize>>> = documents
                .iter()
                .zip(suites)
                .map(|(document, requests)| {
                    let requests = requests.as_ref()?;
                    let mut roots = Vec::new();
                    for request in &requests.requests {
                        roots.push(*by_serial_name.get(request.as_str())?);
                    }
                    roots.extend(entries_of.get(document.suite.as_str()).into_iter().flatten());
                    Some(roots)
                })
                .collect();
            let dispatchers: Vec<usize> = (0..nodes.len())
                .filter(|&node| declaration(node).kind == Kind::Function && !declaration(node).entries.is_empty())
                .collect();
            // A suite with authored parts and no entry needs the guard of `suite_reach`; any other suite has nothing to find.
            let keyed: Vec<bool> = documents
                .iter()
                .zip(suites)
                .map(|(document, requests)| {
                    let has_parts = authored_parts.map_or_else(
                        || requests.as_ref().is_some_and(|requests| requests.authored),
                        |parts| parts.contains(&document.suite),
                    );
                    entries_of.contains_key(document.suite.as_str()) || !has_parts
                })
                .collect();
            (successors, gated, suite_roots, global_roots, dispatchers, keyed)
        };
        let runnable_names = sources
            .iter()
            .flat_map(|source| &source.declarations)
            .filter(|declaration| matches!(declaration.kind, Kind::Function | Kind::Property))
            .map(|declaration| declaration.name.clone())
            .collect();
        let mut index = Self {
            runnable_names,
            files,
            sources,
            nodes,
            node_of,
            successors,
            gated,
            suite_roots,
            dispatchers,
            keyed,
            global: Walk::default(),
            suite_reach: documents.iter().map(|_| OnceLock::new()).collect(),
            suite_ids: documents.iter().map(|document| document.suite.clone()).collect(),
            vocabularies: suites
                .iter()
                .map(|requests| requests.as_ref().map(|requests| requests.vocabulary.clone()).unwrap_or_default())
                .collect(),
        };
        let mut global = Walk::new(index.nodes.len());
        global.run(&index, &global_roots);
        index.global = global;
        index
    }

    fn declaration(&self, node: usize) -> &kotlin::Declaration {
        let (file, index) = self.nodes[node];
        &self.sources[file].declarations[index]
    }

    /// Whether the index holds the Kotlin file `path`.
    pub(crate) fn file(&self, path: &str) -> Option<usize> {
        self.files.binary_search_by(|file| file.as_str().cmp(path)).ok()
    }

    /// What the suite of document `suite` reaches, or `None` when it reaches every changed declaration.
    fn suite_reach(&self, suite: usize) -> Option<&[bool]> {
        self.suite_reach[suite]
            .get_or_init(|| {
                let roots = self.suite_roots[suite].as_ref()?;
                let mut walk = self.global.clone();
                walk.run(self, roots);
                // A suite with authored parts that reaches a dispatcher keyed by suite, and has no entry there, runs a body
                // that the reach cannot find.
                if !self.keyed[suite] && self.dispatchers.iter().any(|&node| walk.seen[node]) {
                    return None;
                }
                Some(walk.seen)
            })
            .as_deref()
    }

    /// What a change of the Kotlin file `file`, now `current`, reaches against its `baseline` text.
    ///
    /// The whole file counts as changed for a new file, for a baseline that cannot be read, and for a file equal to its
    /// baseline, which a caller names to ask about the whole file. A file whose changes are all comments changes no
    /// declaration and reaches no suite.
    pub(crate) fn reach(&self, file: usize, current: &str, baseline: BaselineText<'_>) -> Reach {
        let changed = match baseline {
            BaselineText::Text(baseline) if baseline != current => match self.changed(file, baseline) {
                Some(changed) => changed,
                None => return Reach::Suites(BTreeMap::new()),
            },
            _ => self.whole_file(file),
        };
        let Changed {
            nodes,
            entries,
            whole_file,
        } = changed;
        if nodes.iter().any(|&node| self.global.seen[node]) {
            return Reach::LaneWide;
        }
        let file_name = self.files[file].rsplit('/').next().unwrap_or(&self.files[file]);
        let via = |path: &str| {
            if whole_file {
                format!("{VIA_DECLARATION}{file_name}")
            } else {
                format!("{VIA_DECLARATION}{path}")
            }
        };
        let mut reached: BTreeMap<usize, BTreeSet<String>> = BTreeMap::new();
        for suite in 0..self.suite_ids.len() {
            let reach = self.suite_reach(suite);
            for &node in &nodes {
                if reach.is_none_or(|seen| seen[node]) {
                    reached.entry(suite).or_default().insert(via(&self.declaration(node).path));
                }
            }
        }
        for (suite, path) in &entries {
            if let Some(index) = self.suite_ids.iter().position(|id| id == suite) {
                reached.entry(index).or_default().insert(via(path));
            }
        }
        Reach::Suites(reached)
    }

    /// Every declaration and every suite-keyed entry of `file`, as changed.
    fn whole_file(&self, file: usize) -> Changed {
        Changed {
            nodes: self.node_of[file].iter().copied().collect(),
            entries: self.sources[file]
                .declarations
                .iter()
                .flat_map(|declaration| declaration.entries.keys().map(|suite| (suite.clone(), declaration.path.clone())))
                .collect(),
            whole_file: true,
        }
    }

    /// What of `file` changed against its `baseline` text, or `None` when the two texts differ only in comments and
    /// spaces. A change of tokens outside every declaration, such as the package line, changes the whole file.
    fn changed(&self, file: usize, baseline: &str) -> Option<Changed> {
        let current = &self.sources[file];
        let suite_ids: HashSet<&str> = self.suite_ids.iter().map(String::as_str).collect();
        let before = kotlin::read(baseline, &|key| suite_ids.contains(key));
        let mut nodes = BTreeSet::new();
        let mut entries = BTreeSet::new();
        let mut removed_names = BTreeSet::new();
        for (index, declaration) in current.declarations.iter().enumerate() {
            let old = before.find(&declaration.path).map(|old| &before.declarations[old]);
            if old.is_none_or(|old| old.fingerprint != declaration.fingerprint) {
                nodes.insert(self.node_of[file][index]);
            }
            let old_entries = old.map(|old| &old.entries);
            for (suite, entry) in &declaration.entries {
                if old_entries.and_then(|old| old.get(suite)) != Some(entry) {
                    entries.insert((suite.clone(), declaration.path.clone()));
                    // The map creates a pair for every user, so a changed pair that runs a function or a property of the
                    // sources changes the map itself. A pair of constructors and literals changes its suite alone.
                    if entry.pair && entry.references.iter().any(|name| self.runnable_names.contains(name)) {
                        nodes.insert(self.node_of[file][index]);
                    }
                }
            }
            for suite in old_entries.into_iter().flat_map(BTreeMap::keys) {
                if !declaration.entries.contains_key(suite) {
                    entries.insert((suite.clone(), declaration.path.clone()));
                }
            }
        }
        for old in &before.declarations {
            if current.find(&old.path).is_some() {
                continue;
            }
            removed_names.insert(old.name.clone());
            // A removed type member changes its type, which the callers of the member reach.
            if let Some((owner, _)) = old.path.rsplit_once('.')
                && let Some(index) = current.find(owner)
            {
                nodes.insert(self.node_of[file][index]);
            }
            for suite in old.entries.keys() {
                entries.insert((suite.clone(), old.path.clone()));
            }
        }
        // An added, removed or changed import can change what a name means in the file, so each declaration that names
        // it changed.
        let imported: BTreeSet<&str> = current
            .imports
            .symmetric_difference(&before.imports)
            .map(|import| kotlin::imported_name(import))
            .collect();
        for (index, declaration) in current.declarations.iter().enumerate() {
            if declaration.references.iter().any(|name| imported.contains(name.as_str())) {
                nodes.insert(self.node_of[file][index]);
            }
        }
        if !removed_names.is_empty() {
            for node in 0..self.nodes.len() {
                let declaration = self.declaration(node);
                if declaration.references.iter().any(|name| removed_names.contains(name)) {
                    nodes.insert(node);
                }
            }
        }
        if nodes.is_empty() && entries.is_empty() {
            return (before.fingerprint != current.fingerprint).then(|| self.whole_file(file));
        }
        Some(Changed {
            nodes,
            entries,
            whole_file: false,
        })
    }

    /// What a change of the vocabulary file reaches: the suites whose vocabulary holds a changed entry.
    pub(crate) fn vocabulary_reach(&self, current: &str, baseline: BaselineText<'_>) -> Reach {
        let BaselineText::Text(baseline) = baseline else {
            return Reach::LaneWide;
        };
        let Some(changed) = vocabulary_changes(baseline, current) else {
            return Reach::LaneWide;
        };
        if changed.is_empty() {
            return Reach::LaneWide;
        }
        let mut reached: BTreeMap<usize, BTreeSet<String>> = BTreeMap::new();
        for (suite, vocabulary) in self.vocabularies.iter().enumerate() {
            for entry in vocabulary.intersection(&changed) {
                reached.entry(suite).or_default().insert(format!("{VIA_VOCABULARY}{entry}"));
            }
        }
        Reach::Suites(reached)
    }
}

/// The declaration relation of a harness module over a checkout: what a contract test holds the committed sources to.
pub struct DeclarationReport {
    index: DeclarationIndex,
}

impl DeclarationReport {
    /// Reads the sources of the harness module `module` as the join reads them.
    ///
    /// # Errors
    ///
    /// A module that the lane table joins by no declaration, a catalog that cannot be read, or a source that cannot be
    /// read.
    pub fn read(runtime: &dyn Runtime, area: &Area, module: &str) -> Result<Self, String> {
        let reach = area
            .lanes()
            .declaration_reach(module)
            .ok_or_else(|| format!("the lane table joins no source of {module} by declaration"))?;
        let documents = read_suite_documents(runtime, area).map_err(|refused| refused.message)?;
        let requests = par_map(&documents, |document| SuiteRequests::read(runtime, document));
        DeclarationIndex::build(runtime, reach, &documents, &requests).map(|index| Self { index })
    }

    /// Every suite that reaches every changed declaration: a suite with a request that no request type declares, or
    /// with authored parts and no entry.
    pub fn unresolved_suites(&self) -> Vec<String> {
        let index = &self.index;
        (0..index.suite_ids.len())
            .filter(|&suite| index.suite_reach(suite).is_none())
            .map(|suite| index.suite_ids[suite].clone())
            .collect()
    }

    /// The suites whose requests reach the declaration `path` of the repo-relative `file`, by id, or `None` when every
    /// suite reaches it.
    ///
    /// # Errors
    ///
    /// A file that the sources do not hold, or a declaration that the file does not declare.
    pub fn suites_reaching(&self, file: &str, path: &str) -> Result<Option<Vec<String>>, String> {
        let index = &self.index;
        let at = index.file(file).ok_or_else(|| format!("the sources hold no {file}"))?;
        let declaration = index.sources[at].find(path).ok_or_else(|| format!("{file} declares no {path}"))?;
        let node = index.node_of[at][declaration];
        if index.global.seen[node] {
            return Ok(None);
        }
        Ok(Some(
            (0..index.suite_ids.len())
                .filter(|&suite| index.suite_reach(suite).is_none_or(|seen| seen[node]))
                .map(|suite| index.suite_ids[suite].clone())
                .collect(),
        ))
    }
}

/// The names that the in-source supertypes of `node` declare, through every level.
fn inherited_names<'s>(
    node: usize,
    supertypes_of: &dyn Fn(usize) -> Vec<usize>,
    members: &[Vec<usize>],
    declaration: &dyn Fn(usize) -> &'s kotlin::Declaration,
) -> HashSet<&'s str> {
    let mut names = HashSet::new();
    let mut seen = HashSet::from([node]);
    let mut stack = supertypes_of(node);
    while let Some(supertype) = stack.pop() {
        if !seen.insert(supertype) {
            continue;
        }
        names.extend(members[supertype].iter().map(|&member| declaration(member).name.as_str()));
        stack.extend(supertypes_of(supertype));
    }
    names
}

/// What a change of one file changed.
struct Changed {
    nodes: BTreeSet<usize>,
    /// Each changed suite-keyed entry as (suite, declaration path).
    entries: BTreeSet<(String, String)>,
    /// Whether every declaration of the file counts as changed.
    whole_file: bool,
}

/// The reached nodes of a walk, and the members that wait for their type.
#[derive(Clone, Debug, Default)]
struct Walk {
    seen: Vec<bool>,
    waiting: HashMap<usize, Vec<usize>>,
}

impl Walk {
    fn new(nodes: usize) -> Self {
        Self {
            seen: vec![false; nodes],
            waiting: HashMap::new(),
        }
    }

    fn run(&mut self, index: &DeclarationIndex, roots: &[usize]) {
        let mut stack = Vec::new();
        for &root in roots {
            self.offer(index, root, &mut stack);
        }
        while let Some(node) = stack.pop() {
            if self.seen[node] {
                continue;
            }
            self.seen[node] = true;
            for &next in &index.successors[node] {
                self.offer(index, next, &mut stack);
            }
            if let Some(waiting) = self.waiting.remove(&node) {
                stack.extend(waiting);
            }
        }
    }

    fn offer(&mut self, index: &DeclarationIndex, node: usize, stack: &mut Vec<usize>) {
        if self.seen[node] {
            return;
        }
        match index.gated[node] {
            Some(owner) if !self.seen[owner] => self.waiting.entry(owner).or_default().push(node),
            _ => stack.push(node),
        }
    }
}

/// The entries of the vocabulary that changed between two texts, as `<section>/<id>`, or `None` when a part outside
/// the sections of entries changed, or a text is no JSON object.
fn vocabulary_changes(before: &str, after: &str) -> Option<BTreeSet<String>> {
    let before: Value = serde_json::from_str(before).ok()?;
    let after: Value = serde_json::from_str(after).ok()?;
    let (before, after) = (before.as_object()?, after.as_object()?);
    let mut changed = BTreeSet::new();
    let added = after.keys().filter(|key| !before.contains_key(*key));
    for key in before.keys().chain(added) {
        let (old, new) = (before.get(key), after.get(key));
        if old == new {
            continue;
        }
        if !matches!(key.as_str(), "actions" | "assertions" | "authored") {
            return None;
        }
        let empty = serde_json::Map::new();
        let old = old.map_or(Some(&empty), Value::as_object)?;
        let new = new.map_or(Some(&empty), Value::as_object)?;
        for id in old.keys().chain(new.keys()) {
            if old.get(id) != new.get(id) {
                changed.insert(format!("{key}/{id}"));
            }
        }
    }
    Some(changed)
}

#[cfg(test)]
mod tests;
