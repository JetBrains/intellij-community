//! The one host build, stamped: what to launch, and the four identities that decide what can be reused.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use avl_base::fs::create_private_dir;
use avl_base::{Config, Exit, OrRefuse, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::paths::GuestPaths;
use avl_host_sys::runfiles::HostRunfiles;
use avl_report::digest::{self, FileDigestCache, JbrPolicy, PathDigest, ProductIdentityInput};
use avl_wire::daemon::LABEL;
use avl_wire::progress::Event;
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

/// One host build, stamped: what to launch, and the four identities that decide what can be reused.
#[derive(Clone, Debug)]
pub(crate) struct PreparedBuild {
    /// Where each runfile is on the host: under the runfiles tree beside the descriptor, or at its MANIFEST target on a Windows host, where
    /// Bazel builds no tree.
    pub runfiles: HostRunfiles,
    /// The runfiles tree as the guest opens it: [`guest_runfiles_root`] of `runfiles`. Every path the guest gets
    /// under the tree is joined onto this one.
    pub guest_runfiles_root: String,
    pub descriptor: RuntimeDescriptor,
    /// The test tier by host path and content digest, in classpath order: what `/jars` is asked about and what a
    /// missing digest is uploaded from.
    pub hot_jars: Vec<PathDigest>,
    pub runtime_digest: String,
    pub launch_digest: String,
    pub product_digest: String,
    pub mount_digest: String,
    /// The immutable environment the daemon is exec'd with that no build produces: what every lane sets by value,
    /// plus the guest's Node. Its [`lane_environment_digest`] is part of the launch digest, so a change here
    /// re-execs the daemon.
    pub daemon_environment: BTreeMap<String, String>,
    /// The host checkout when the build started, or why it could not be read. The run report carries it; nothing
    /// selects or skips a suite from it.
    pub tree: Result<Tree, Refusal>,
    /// Bazel's cost, and this controller's own hashing, timed apart so neither can hide inside the other.
    pub build: Duration,
    pub stamp: Duration,
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
    /// The checkout's guest root. The key keeps its name, so the digest on a Unix host does not change.
    pub(crate) host_repo: &'a str,
    pub(crate) vm_data: &'a str,
    pub(crate) vm_download_cache: &'a str,
    pub(crate) vm_home: &'a str,
    pub(crate) vm_node: &'a str,
    pub(crate) vm_tmp: &'a str,
    pub(crate) vm_user: &'a str,
}

fn literal_digest(literal: &impl Serialize) -> String {
    // A struct of strings, string slices and integers always serializes.
    let text = serde_json::to_string(literal).unwrap_or_default();
    digest::sha256_text(&text)
}

/// Where the guest opens the runfiles tree of a descriptor.
///
/// The one seam for that question. A tree Bazel built is opened through its share, at its [`GuestPaths`] path. A
/// MANIFEST, which is all a Windows host has, becomes the tree the guest agent builds, and
/// `Guest::ensure_runfiles_tree` builds it before the daemon starts.
pub(crate) fn guest_runfiles_root(settings: &Config, runfiles: &HostRunfiles) -> Result<String, Refusal> {
    runfiles.guest_root(settings)
}

/// The `@controller-boot` digest for one guest runfiles root and port.
///
/// Over the values the guest gets, not over the host paths: the checkout is the guest root the JVM is told, and
/// the runfiles root is the guest one. On a Unix host the two spellings are the same text.
pub(crate) fn controller_boot_digest(settings: &Config, guest_runfiles_root: &str, daemon_port: u16) -> Result<String, Refusal> {
    let paths = GuestPaths::of(settings)?;
    Ok(literal_digest(&ControllerBoot {
        runfiles_root: guest_runfiles_root,
        daemon_port,
        host_repo: paths.repo(),
        vm_data: &settings.vm_data,
        vm_download_cache: &settings.vm_download_cache,
        vm_home: &settings.vm_home,
        vm_node: &settings.vm_node,
        vm_tmp: &settings.vm_tmp,
        vm_user: &settings.vm_user,
    }))
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

/// The digests of one descriptor's inputs, which is all the stamp reads from disk.
struct Stamped {
    hot_jars: Vec<PathDigest>,
    runtime_digest: String,
    product_digest: String,
    mount_digest: String,
}

/// Hashes every declared input of a descriptor through the digest cache and composes three of the four identities;
/// the launch digest needs the settings and is composed by the caller. Blocking: the first sweep reads gigabytes.
fn stamp(descriptor: &RuntimeDescriptor, runfiles: &HostRunfiles, cache_path: &Path) -> Result<Stamped, Refusal> {
    let mut cache = FileDigestCache::open(cache_path);
    let mut file = |file: &RuntimeFile| declared_input(&mut cache, runfiles, file, false);

    let mut hot_jars = Vec::with_capacity(descriptor.classpath.hot.len());
    for jar in &descriptor.classpath.hot {
        let declared = file(jar)?;
        hot_jars.push(PathDigest::new(declared.host_path.to_string_lossy(), declared.identity.sha256));
    }
    let mut runtime_entries = Vec::with_capacity(descriptor.classpath.stable.len() + 2);
    for jar in &descriptor.classpath.stable {
        runtime_entries.push(file(jar)?.identity);
    }
    let jbr_archive = file(&descriptor.jbr.archive)?;
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
    runtime_entries.push(jbr_archive.identity);
    let runtime_digest = digest::path_sensitive_digest(&runtime_entries);

    let product_digest = digest::product_identity(&ProductIdentityInput {
        fingerprint: file(&descriptor.dev_dist.fingerprint)?.identity,
        config: file(&descriptor.dev_dist.config)?.identity,
        jbr_manifest: file(&descriptor.jbr.manifest)?.identity,
        jbr_policy: JbrPolicy {
            platform: descriptor.jbr.platform.clone(),
            java_home_suffix: descriptor.jbr.java_home_suffix.clone(),
            preloaded_only: descriptor.jbr.preloaded_only,
        },
    });

    let mut mount_entries = vec![PathDigest::new("@product", &product_digest)];
    for data in &descriptor.data {
        mount_entries.push(declared_input(&mut cache, runfiles, data, true)?.identity);
    }
    let mount_digest = digest::path_sensitive_digest(&mount_entries);

    cache.save().or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot save the digest cache {}", cache_path.display())
    })?;
    Ok(Stamped {
        hot_jars,
        runtime_digest,
        product_digest,
        mount_digest,
    })
}

impl Host {
    /// Runs the one host build and stamps it: the four digests, the hot tier by content, and the launch identity
    /// everything downstream compares.
    ///
    /// The build fixes the bytes that a run pushes, so the checkout tree is read when the build starts, beside it,
    /// and adds no time to it.
    pub(crate) async fn prepare_build(&self, ctx: &Ctx, scope: &BuildScope) -> Result<PreparedBuild, Refusal> {
        let build_started = Instant::now();
        let (built, tree) = tokio::join!(
            self.bazel.build(ctx, &scope.log, LABEL),
            read_checkout_tree(ctx, &self.runner, &self.settings)
        );
        built?;
        self.reporter.publish(Event::Checkout(checkout_event(&tree)), None);
        let descriptor_path = self.bazel.runtime_descriptor(ctx, LABEL).await?;
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
        let stamped = {
            let _one_sweep = self.stamping.lock().await;
            let (descriptor, runfiles, cache) = (descriptor.clone(), runfiles.clone(), scope.digest_cache.clone());
            tokio::task::spawn_blocking(move || stamp(&descriptor, &runfiles, &cache))
                .await
                .or_refuse("internal_error", Exit::FAILURE, || "the digest sweep failed".to_owned())??
        };

        let guest_runfiles_root = guest_runfiles_root(&self.settings, &runfiles)?;
        let boot = controller_boot_digest(&self.settings, &guest_runfiles_root, self.settings.daemon.port)?;
        let daemon_environment = crate::lane::daemon_environment(&self.settings);
        let launch_digest = launch_digest(&stamped.runtime_digest, &boot, &daemon_environment);
        Ok(PreparedBuild {
            runfiles,
            guest_runfiles_root,
            descriptor,
            hot_jars: stamped.hot_jars,
            runtime_digest: stamped.runtime_digest,
            launch_digest,
            product_digest: stamped.product_digest,
            mount_digest: stamped.mount_digest,
            daemon_environment,
            tree,
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
