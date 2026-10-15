//! The one host build, stamped: what to launch, and the four identities that decide what can be reused.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use avl_base::GuestOs;
use avl_base::format::seconds;
use avl_base::fs::create_private_dir;
use avl_base::{Config, Exit, OrRefuse, PhaseRun, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::runfiles::{GuestRunfilesTree, GuestTreeInputs, HostRunfiles};
use avl_report::digest::{self, FileDigestCache, PathDigest, ProductIdentityInput};
use avl_wire::daemon::{LABEL, LINUX_GUEST_LABEL, Runner};
use avl_wire::progress::{Event, Phase};
use avl_wire::report::Tree;
use avl_wire::runtime::{self, RuntimeDescriptor, RuntimeFile};
use serde::Serialize;

use crate::daemon::host::Host;
use crate::daemon::tree::{checkout_event, read_checkout_tree};

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The path-sensitive identity of the immutable environment a daemon receives at exec. By name, in byte order.
pub(crate) fn lane_environment_digest(environment: &BTreeMap<String, String>) -> String {
    let entries: Vec<PathDigest> = environment
        .iter()
        .map(|(name, value)| PathDigest::new(name, digest::sha256_text(value)))
        .collect();
    digest::path_sensitive_digest(&entries)
}

/// The daemon target of a guest: one target per guest OS, because the guest is a target and not a build option.
pub(crate) const fn daemon_label(guest_os: GuestOs) -> &'static str {
    match guest_os {
        GuestOs::Macos => LABEL,
        GuestOs::Linux => LINUX_GUEST_LABEL,
    }
}

/// One host build, stamped: what to launch, and the four identities that decide what can be reused.
#[derive(Clone, Debug)]
pub(crate) struct PreparedBuild {
    /// The daemon target this build built ([`daemon_label`]).
    pub label: &'static str,
    /// The runfiles tree the guest builds from the MANIFEST of `runfiles`, with the bytes of its staged runfiles.
    pub guest_tree: GuestRunfilesTree,
    /// The root of [`Self::guest_tree`], as the guest opens it. Every path the guest gets under the tree is joined onto
    /// this one.
    pub guest_runfiles_root: String,
    pub descriptor: RuntimeDescriptor,
    /// The test tier by host path and content digest, in classpath order: what `/jars` is asked about and what a
    /// missing digest is uploaded from.
    pub hot_jars: Vec<PathDigest>,
    /// The files of the context plugins, in descriptor order: pushed with the hot tier, and installed by the daemon into
    /// the plugin directory of the IDE context.
    pub plugin_files: Vec<PluginFile>,
    /// The lane inputs of the descriptor, in key order: pushed with the hot tier. No identity reads them, so an edit of
    /// the fake agent or of a document restages nothing and restarts nothing.
    pub lane_inputs: Vec<LaneInput>,
    pub runtime_digest: String,
    pub launch_digest: String,
    pub product_digest: String,
    pub mount_digest: String,
    /// The identity of [`Self::plugin_files`]: each destination with its content digest. Neither the product digest
    /// nor the mount digest reads it, so a change of the context plugins alone refreshes no share.
    pub plugins_digest: String,
    /// The digest of each context plugin directory, such as the bridge plugin and its resident part, by directory
    /// name. The lane daemon decides by them whether a change reloads the bridge plugin or relaunches the IDE.
    pub plugin_directories: BTreeMap<String, String>,
    /// The immutable environment the daemon is exec'd with that no build produces: what every lane sets by value,
    /// plus the guest's Node. Its [`lane_environment_digest`] is part of the launch digest, so a change here
    /// re-execs the daemon.
    pub daemon_environment: BTreeMap<String, String>,
    /// The host checkout when the build started, or why it could not be read. The run report carries it; nothing
    /// selects or skips a suite from it.
    pub tree: Result<Tree, Refusal>,
    /// The build of the IDE without its product code, from the `build.txt` of the distribution, or why it cannot be
    /// read. Only the IJ Perf files name it, so a distribution without the file still runs.
    pub ide_build: Result<String, String>,
    /// Bazel's cost, and this controller's own hashing, timed apart so neither can hide inside the other.
    pub build: Duration,
    pub stamp: Duration,
}

/// One file of a context plugin: its destination below the plugin directory of the IDE context, and its bytes on the
/// host by path and content digest, as `/jars` is asked about them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct PluginFile {
    pub destination: String,
    pub host: PathDigest,
}

/// One lane input of the descriptor: its key in the `ide` section, such as `fakeAgentJar`, and its bytes on the host by
/// path and content digest, as `/jars` is asked about them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LaneInput {
    pub field: &'static str,
    pub host: PathDigest,
}

/// The note that names the digest of each lane input, in key order, as `<key> <first 12 hex digits>`.
pub(crate) fn lane_inputs_note(inputs: &[LaneInput]) -> String {
    let named: Vec<String> = inputs
        .iter()
        .map(|input| format!("{} {}", input.field, input.host.sha256.get(..12).unwrap_or(&input.host.sha256)))
        .collect();
    format!("lane inputs by digest: {}", named.join(", "))
}

/// The digest of each context plugin directory, by the formula of the lane daemon and of the resident plugin.
pub(crate) fn plugin_directories(files: &[PluginFile]) -> BTreeMap<String, String> {
    avl_wire::daemon::plugin_directory_digests(files.iter().map(|file| (file.destination.as_str(), file.host.sha256.as_str())))
}

/// The note that names the digest of each context plugin directory, in name order, as `<directory> <first 12 hex
/// digits>`.
pub(crate) fn plugin_directories_note(directories: &BTreeMap<String, String>) -> String {
    let named: Vec<String> = directories
        .iter()
        .map(|(directory, digest)| format!("{directory} {}", digest.get(..12).unwrap_or(digest)))
        .collect();
    format!("context plugins by digest: {}", named.join(", "))
}

/// The identity of the context plugin files: each destination with its content digest, so a moved file and a changed
/// file both change it.
pub(crate) fn plugins_digest(files: &[PluginFile]) -> String {
    let entries: Vec<PathDigest> = files
        .iter()
        .map(|file| PathDigest::new(&file.destination, &file.host.sha256))
        .collect();
    digest::path_sensitive_digest(&entries)
}

/// Where one host build writes its log and caches its file digests.
///
/// A worker used to supply exactly these two paths and nothing else, which made a build look per-worker when it
/// never was: the Bazel outputs it reads are identical for every worker of a guest. Naming the two paths lets N
/// workers share one build and - the part that pays - one stat-keyed [`FileDigestCache`]: the first shard warms it
/// and the rest hit it instead of re-hashing the same hundreds of megabytes of classpath.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct BuildScope {
    pub log: PathBuf,
    pub digest_cache: PathBuf,
}

impl BuildScope {
    fn under(directory: &Path) -> Result<Self, Refusal> {
        create_private_dir(directory)?;
        Ok(Self {
            log: directory.join("host-build.log"),
            digest_cache: directory.join("daemon-digests.json"),
        })
    }

    /// One worker's own build and digest cache.
    pub(crate) fn worker(settings: &Config, worker: &str) -> Result<Self, Refusal> {
        Self::under(&settings.worker_dir(worker))
    }

    /// One build and one digest cache for every worker of this pool, which all read the same host outputs.
    pub(crate) fn pool(settings: &Config) -> Result<Self, Refusal> {
        Self::under(&settings.runtime_root.join("shared-build"))
    }
}

// --- the digested literals ---------------------------------------------------------------------------------------
//
// Each is hashed as the compact JSON of the struct. The field order is part of the digest; changing it restarts
// every daemon once, which is harmless and is why nothing else pins these bytes.

/// `@static-launch`: the launch facts of the daemon JVM that no file carries.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct StaticLaunch<'a> {
    schema_version: u32,
    main_class: &'a str,
    static_jvm_flags: &'a [String],
    jbr_platform: &'a str,
    java_home_suffix: &'a str,
}

/// `@controller-boot`: every controller-chosen boot setting a daemon is exec'd with. The daemon port is
/// deliberately inside it - changing the port must restart the daemon, because the running one keeps listening
/// where it was told to.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ControllerBoot<'a> {
    pub(crate) runfiles_root: &'a str,
    pub(crate) daemon_port: u16,
    /// The home of the daemon JVM, an empty directory of the staged generation.
    pub(crate) guest_home: &'a str,
    pub(crate) vm_data: &'a str,
    pub(crate) vm_download_cache: &'a str,
    pub(crate) vm_home: &'a str,
    pub(crate) vm_node: &'a str,
    pub(crate) vm_tmp: &'a str,
    pub(crate) vm_user: &'a str,
    /// The `lane-daemon` verb, or absent for the JVM daemon, so the digest of a JVM daemon stays what it was.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) runner: Option<&'a str>,
}

fn literal_digest(literal: &impl Serialize) -> String {
    // A struct of strings, string slices and integers always serializes.
    let text = serde_json::to_string(literal).unwrap_or_default();
    digest::sha256_text(&text)
}

/// The `@controller-boot` digest for one guest runfiles root, port and runtime generation.
///
/// Over the values the guest gets, not over the host paths: the runfiles root is the tree the guest builds, and the
/// home is the directory of the generation that the JVM is told.
pub(crate) fn controller_boot_digest(settings: &Config, guest_runfiles_root: &str, daemon_port: u16, runtime_digest: &str) -> String {
    let guest_home = crate::daemon::stage::guest_home(settings, runtime_digest);
    literal_digest(&ControllerBoot {
        runfiles_root: guest_runfiles_root,
        daemon_port,
        guest_home: &guest_home,
        vm_data: &settings.vm_data,
        vm_download_cache: &settings.vm_download_cache,
        vm_home: &settings.vm_home,
        vm_node: &settings.vm_node,
        vm_tmp: &settings.vm_tmp,
        vm_user: &settings.vm_user,
        runner: (settings.daemon.runner != Runner::Jvm).then(|| settings.daemon.runner.as_str()),
    })
}

/// The launch digest over its three parts. A JVM cannot change its own environment, so a change to the daemon
/// environment has to re-exec the daemon rather than stay invisible until someone restarts it by hand.
pub(crate) fn launch_digest(runtime_digest: &str, controller_boot_digest: &str, daemon_environment: &BTreeMap<String, String>) -> String {
    digest::path_sensitive_digest(&[
        PathDigest::new("@runtime", runtime_digest),
        PathDigest::new("@controller-boot", controller_boot_digest),
        PathDigest::new("@daemon-environment", lane_environment_digest(daemon_environment)),
    ])
}

// --- the stamp ---------------------------------------------------------------------------------------------------

/// One declared runfile's identity (logical path and digest) plus where its bytes actually are on the host.
pub(crate) struct Declared {
    pub(crate) identity: PathDigest,
    pub(crate) host_path: PathBuf,
}

/// A declared runfile, which must be a regular file (`allow_directory` false) or may also be a directory (declared
/// data such as an unpacked npm package, digested as a tree).
pub(crate) fn declared_input(
    cache: &mut FileDigestCache,
    runfiles: &HostRunfiles,
    file: &RuntimeFile,
    allow_directory: bool,
) -> Result<Declared, Refusal> {
    let missing = |cause: String| {
        Refusal::new(
            "daemon_runtime_input_missing",
            Exit::SOFTWARE,
            format!(
                "{} ({}) is not a file in {}{cause}",
                file.logical_path,
                file.owner,
                runfiles.location().display()
            ),
        )
    };
    let Some(host_path) = runfiles.host_path(&file.logical_path) else {
        return Err(missing(String::new()));
    };
    let acceptable = fs::metadata(&host_path).is_ok_and(|metadata| metadata.is_file() || (allow_directory && metadata.is_dir()));
    if !acceptable {
        return Err(missing(String::new()));
    }
    let sha256 = cache.digest(&host_path).map_err(|error| missing(format!(": {error}")))?;
    Ok(Declared {
        identity: PathDigest::new(&file.logical_path, sha256),
        host_path,
    })
}

/// The JBR of the distribution, which the daemon JVM runs on a copy of: its host directory, digested as a tree.
///
/// The identity is named by the logical path of the directory, so the runtime digest moves when the JBR bytes
/// or modes move, and not when another file of the distribution does. A distribution without the directory is a
/// refusal that names the path.
pub(crate) fn jbr_input(
    cache: &mut FileDigestCache,
    runfiles: &HostRunfiles,
    descriptor: &RuntimeDescriptor,
) -> Result<PathDigest, Refusal> {
    let home = &descriptor.dev_dist.home;
    let logical_path = format!("{}/{}", home.logical_path, descriptor.jbr.directory);
    let missing = |path: &Path, cause: String| {
        Refusal::new(
            "daemon_runtime_input_missing",
            Exit::SOFTWARE,
            format!(
                "the distribution {} ({}) has no JBR: {} is not a directory{cause}; the dev distribution places the \
                 production JBR there",
                home.logical_path,
                home.owner,
                path.display()
            ),
        )
    };
    let Some(home_path) = runfiles.host_path(&home.logical_path) else {
        return Err(missing(&runfiles.location().join(&logical_path), String::new()));
    };
    let path = home_path.join(&descriptor.jbr.directory);
    if !fs::metadata(&path).is_ok_and(|metadata| metadata.is_dir()) {
        return Err(missing(&path, String::new()));
    }
    let sha256 = cache.digest(&path).map_err(|error| missing(&path, format!(": {error}")))?;
    Ok(PathDigest::new(logical_path, sha256))
}

/// The build of the IDE of `descriptor`: the `build.txt` of its distribution without the product code, or why it cannot
/// be read.
fn ide_build(runfiles: &HostRunfiles, descriptor: &RuntimeDescriptor) -> Result<String, String> {
    let home = &descriptor.dev_dist.home.logical_path;
    let path = runfiles
        .host_path(home)
        .ok_or_else(|| format!("the runfiles hold no distribution {home}"))?
        .join(BUILD_TXT);
    let text = fs::read_to_string(&path).map_err(|error| format!("cannot read {}: {error}", path.display()))?;
    match avl_report::performance::build_without_product_code(&text) {
        build if build.is_empty() => Err(format!("{} is empty", path.display())),
        build => Ok(build),
    }
}

/// The file of an IDE distribution that names its build, such as `IU-271.1234`.
const BUILD_TXT: &str = "build.txt";

/// The digests of one descriptor's inputs, which is all the stamp reads from disk.
struct Stamped {
    guest_tree: GuestRunfilesTree,
    hot_jars: Vec<PathDigest>,
    plugin_files: Vec<PluginFile>,
    lane_inputs: Vec<LaneInput>,
    runtime_digest: String,
    product_digest: String,
    mount_digest: String,
    parts: StampParts,
}

/// How long each part of [`stamp`] took, so the journal names the part that grew.
#[derive(Clone, Copy, Debug, Default)]
struct StampParts {
    open: Duration,
    digests: Duration,
    save: Duration,
    tree: Duration,
}

/// Hashes every declared input of a descriptor through the digest cache and composes three of the four identities;
/// the launch digest needs the settings and is composed by the caller. The context plugin files are hashed here too,
/// and their identity is composed apart from the other four. The lane inputs are hashed for the push, and no identity
/// reads them. The guest runfiles tree is planned here too, because the
/// plan reads each staged runfile. Blocking: the first sweep reads gigabytes.
fn stamp(
    descriptor: &RuntimeDescriptor,
    runfiles: &HostRunfiles,
    cache_path: &Path,
    tree_inputs: &GuestTreeInputs,
) -> Result<Stamped, Refusal> {
    let started = Instant::now();
    let mut cache = FileDigestCache::open(cache_path);
    let opened = Instant::now();
    let jbr = jbr_input(&mut cache, runfiles, descriptor)?;
    let mut file = |file: &RuntimeFile| declared_input(&mut cache, runfiles, file, false);

    let mut hot_jars = Vec::with_capacity(descriptor.classpath.hot.len());
    for jar in &descriptor.classpath.hot {
        let declared = file(jar)?;
        hot_jars.push(PathDigest::new(declared.host_path.to_string_lossy(), declared.identity.sha256));
    }
    let mut plugin_files = Vec::with_capacity(descriptor.ide.plugins.len());
    for plugin in &descriptor.ide.plugins {
        let declared = file(&plugin.file)?;
        plugin_files.push(PluginFile {
            destination: plugin.destination.clone(),
            host: PathDigest::new(declared.host_path.to_string_lossy(), declared.identity.sha256),
        });
    }
    let mut lane_inputs = Vec::with_capacity(3);
    for (field, input) in descriptor.ide.lane_inputs() {
        let declared = file(input)?;
        lane_inputs.push(LaneInput {
            field,
            host: PathDigest::new(declared.host_path.to_string_lossy(), declared.identity.sha256),
        });
    }
    let mut runtime_entries = Vec::with_capacity(descriptor.classpath.stable.len() + 2);
    for jar in &descriptor.classpath.stable {
        runtime_entries.push(file(jar)?.identity);
    }
    runtime_entries.push(PathDigest::new(
        "@static-launch",
        literal_digest(&StaticLaunch {
            schema_version: descriptor.schema_version,
            main_class: &descriptor.main_class,
            static_jvm_flags: &descriptor.static_jvm_flags,
            jbr_platform: &descriptor.jbr.platform,
            java_home_suffix: &descriptor.jbr.java_home_suffix,
        }),
    ));
    runtime_entries.push(jbr);
    let runtime_digest = digest::path_sensitive_digest(&runtime_entries);

    let distribution = digest::product_identity(&ProductIdentityInput {
        fingerprint: file(&descriptor.dev_dist.fingerprint)?.identity,
        config: file(&descriptor.dev_dist.config)?.identity,
    });
    let product_digest = digest::path_sensitive_digest(&[
        PathDigest::new("@distribution", &distribution),
        file(&descriptor.ide.flags_file)?.identity,
    ]);

    let mut mount_entries = vec![
        PathDigest::new("@product", &product_digest),
        file(&descriptor.ide.project_archive)?.identity,
        PathDigest::new("@project-root", digest::sha256_text(&descriptor.ide.project_root)),
    ];
    for data in &descriptor.data {
        mount_entries.push(declared_input(&mut cache, runfiles, data, true)?.identity);
    }
    let mount_digest = digest::path_sensitive_digest(&mount_entries);

    let digested = Instant::now();
    cache.save().or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot save the digest cache {}", cache_path.display())
    })?;
    let saved = Instant::now();
    let guest_tree = runfiles.guest_tree(tree_inputs)?;
    Ok(Stamped {
        guest_tree,
        hot_jars,
        plugin_files,
        lane_inputs,
        runtime_digest,
        product_digest,
        mount_digest,
        parts: StampParts {
            open: opened - started,
            digests: digested - opened,
            save: saved - digested,
            tree: saved.elapsed(),
        },
    })
}

impl Host {
    /// Runs the one host build and stamps it: the four digests, the hot tier by content, and the launch identity
    /// everything downstream compares.
    ///
    /// The build fixes the bytes that a run pushes, so the checkout tree is read when the build starts, beside it,
    /// and adds no time to it.
    ///
    /// The work after the build is the phase [`Phase::Prepare`]. The build names the descriptor in its event file, so
    /// the phase is the digest sweep and asks Bazel nothing.
    pub(crate) async fn prepare_build(&self, ctx: &Ctx, scope: &BuildScope) -> Result<PreparedBuild, Refusal> {
        let build_started = Instant::now();
        let label = daemon_label(self.settings.guest_os);
        let (built, tree) = tokio::join!(
            self.bazel.build(ctx, &scope.log, label),
            read_checkout_tree(ctx, &self.runner, &self.settings)
        );
        built?;
        self.reporter.publish(Event::Checkout(checkout_event(&tree)), None);
        let phase = self.reporter.start_phase(Phase::Prepare, "hash what the host build produced", None);
        let prepared = self.prepare_built(ctx, scope, label, build_started, tree, &phase).await;
        phase.finish(&prepared);
        prepared
    }

    /// The descriptor and the digest sweep of [`Host::prepare_build`], after its build.
    async fn prepare_built(
        &self,
        ctx: &Ctx,
        scope: &BuildScope,
        label: &'static str,
        build_started: Instant,
        tree: Result<Tree, Refusal>,
        phase: &PhaseRun,
    ) -> Result<PreparedBuild, Refusal> {
        let lookup_started = Instant::now();
        let descriptor_path = self.bazel.runtime_descriptor(ctx, label).await?;
        let lookup = lookup_started.elapsed();
        let build = build_started.elapsed();

        let stamp_started = Instant::now();
        let content = fs::read(&descriptor_path).or_refuse("daemon_runtime_descriptor_missing", Exit::SOFTWARE, || {
            format!("cannot read the runtime descriptor {}", descriptor_path.display())
        })?;
        let descriptor = runtime::parse_runtime_descriptor(
            &content,
            &descriptor_path,
            crate::lane::guest_jbr_platform(self.settings.guest_os, self.settings.guest_arch),
        )
        .map_err(avl_base::descriptor_refusal)?;
        let runfiles = HostRunfiles::of(&descriptor_path)?;
        let ide_build = ide_build(&runfiles, &descriptor);
        let tree_inputs = GuestTreeInputs::of(&self.settings)?;
        let stamped = {
            let _one_sweep = self.stamping.lock().await;
            let (descriptor, runfiles, cache) = (descriptor.clone(), runfiles.clone(), scope.digest_cache.clone());
            tokio::task::spawn_blocking(move || stamp(&descriptor, &runfiles, &cache, &tree_inputs))
                .await
                .or_refuse("internal_error", Exit::FAILURE, || "the digest sweep failed".to_owned())??
        };
        let StampParts {
            open,
            digests,
            save,
            tree: planned,
        } = stamped.parts;
        let text = |duration: Duration| seconds(duration.as_secs_f64() * 1000.0);
        phase.progress(&format!(
            "descriptor {}  cache open {}  digests {}  cache save {}  runfiles tree {}",
            text(lookup),
            text(open),
            text(digests),
            text(save),
            text(planned)
        ));

        let guest_runfiles_root = stamped.guest_tree.root.clone();
        let boot = controller_boot_digest(
            &self.settings,
            &guest_runfiles_root,
            self.settings.daemon.port,
            &stamped.runtime_digest,
        );
        let daemon_environment = crate::lane::daemon_environment(&self.settings);
        let launch_digest = launch_digest(&stamped.runtime_digest, &boot, &daemon_environment);
        Ok(PreparedBuild {
            label,
            guest_tree: stamped.guest_tree,
            guest_runfiles_root,
            descriptor,
            hot_jars: stamped.hot_jars,
            plugins_digest: plugins_digest(&stamped.plugin_files),
            plugin_directories: plugin_directories(&stamped.plugin_files),
            plugin_files: stamped.plugin_files,
            lane_inputs: stamped.lane_inputs,
            runtime_digest: stamped.runtime_digest,
            launch_digest,
            product_digest: stamped.product_digest,
            mount_digest: stamped.mount_digest,
            daemon_environment,
            tree,
            ide_build,
            build,
            stamp: stamp_started.elapsed(),
        })
    }
}

impl PreparedBuild {
    /// Why this iteration cannot be attributed to the current checkout, or `None`.
    ///
    /// The guest runs the jars this iteration pushed, so a host rebuild during the run does not corrupt the
    /// execution. What it corrupts is the attribution: the verdict is about bytes the checkout no longer produces,
    /// and a reader maps it to the source in their editor. Bazel replaces a jar by rename, so nothing else notices
    /// at all - the run keeps its open file handles and reports green.
    ///
    /// Hashing rather than comparing a stat, because the digest is what every other comparison here is made of,
    /// and the hot tier is the test jars alone.
    pub(crate) fn hot_jar_drift(&self) -> Option<String> {
        let moved: Vec<String> = self
            .hot_jars
            .iter()
            .filter_map(|jar| {
                let path = Path::new(&jar.path);
                let name = path
                    .file_name()
                    .map_or_else(|| jar.path.clone(), |name| name.to_string_lossy().into_owned());
                match digest::sha256_file(path) {
                    Err(error) => Some(format!("{name} (unreadable: {error})")),
                    Ok(sum) if sum != jar.sha256 => Some(name),
                    Ok(_) => None,
                }
            })
            .collect();
        if moved.is_empty() {
            return None;
        }
        Some(format!(
            "{} test jar(s) were rebuilt on the host while this iteration ran, so its verdict describes bytes this \
             checkout no longer produces: {}. Re-run the iteration, and do not build while a run is active",
            moved.len(),
            moved.join(", ")
        ))
    }
}
