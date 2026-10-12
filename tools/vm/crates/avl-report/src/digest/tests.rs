use std::fs;

use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

// --- the identities -------------------------------------------------------------------------------------------

#[test]
fn sha256_text_is_the_ordinary_sha256_of_the_bytes() {
    assert_eq!(sha256_text(""), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    // Hashed as UTF-8 bytes: a port that hashed UTF-16 would agree on every ASCII input and on nothing else.
    assert_eq!(sha256_text("é"), hex::encode(Sha256::digest([0xc3_u8, 0xa9])));
}

#[test]
fn the_path_sensitive_digest_cannot_be_confused_by_concatenation() {
    assert_ne!(
        path_sensitive_digest(&[PathDigest::new("a", "bc")]),
        path_sensitive_digest(&[PathDigest::new("ab", "c")])
    );
}

/// The guest's parity layout makes host and guest paths identical, so an identity over contents alone would call
/// two different layouts one generation.
#[test]
fn the_path_sensitive_digest_sees_both_order_and_path() {
    let first = PathDigest::new("a.jar", "1".repeat(64));
    let second = PathDigest::new("b.jar", "2".repeat(64));
    let ordered = path_sensitive_digest(&[first.clone(), second.clone()]);
    assert_ne!(
        ordered,
        path_sensitive_digest(&[second.clone(), first.clone()]),
        "a reordered classpath has the same identity"
    );
    let renamed = PathDigest::new("c.jar", first.sha256);
    assert_ne!(
        ordered,
        path_sensitive_digest(&[renamed, second]),
        "a renamed file with identical bytes has the same identity"
    );
}

/// The guest policy is folded in as the text of a JSON object, so its key order is contract: reordering it
/// changes `productDigest` for the whole fleet.
#[test]
fn the_product_identity_folds_the_guest_policy_in_with_its_keys_in_order() {
    let input = ProductIdentityInput {
        fingerprint: PathDigest::new("fp", "1"),
        config: PathDigest::new("cfg", "2"),
        jbr_manifest: PathDigest::new("jbr", "5"),
        jbr_policy: JbrPolicy {
            platform: "linux-x64".to_owned(),
            java_home_suffix: String::new(),
            preloaded_only: true,
        },
    };
    let want = path_sensitive_digest(&[
        PathDigest::new("fp", "1"),
        PathDigest::new("cfg", "2"),
        PathDigest::new("jbr", "5"),
        PathDigest::new(
            "@guest-policy",
            sha256_text(r#"{"platform":"linux-x64","javaHomeSuffix":"","preloadedOnly":true}"#),
        ),
    ]);
    assert_eq!(product_identity(&input), want);
    // The same files under a different Java-home suffix are a different distribution.
    let mut other = input;
    other.jbr_policy.java_home_suffix = "Contents/Home".to_owned();
    assert_ne!(product_identity(&other), want);
}

#[test]
fn sha256_file_streams_what_is_on_disk() {
    let root = tempfile::tempdir().unwrap();
    let path = root.path().join("jar");
    fs::write(&path, "air").unwrap();
    assert_eq!(sha256_file(&path).unwrap(), sha256_text("air"));
    sha256_file(&root.path().join("absent")).unwrap_err();
}

// --- the digest cache -----------------------------------------------------------------------------------------

/// The saved cache is proven to be *consulted* rather than merely present, by poisoning one entry: only a reader
/// that trusts the stat key can answer the poisoned digest.
#[test]
fn the_digest_cache_answers_from_disk_when_the_stat_key_did_not_move() {
    let root = tempfile::tempdir().unwrap();
    let jar = root.path().join("util.jar");
    fs::write(&jar, "one").unwrap();
    let cache_path = root.path().join("daemon-digests.json");

    let mut cache = FileDigestCache::open(&cache_path);
    assert_eq!(cache.digest(&jar).unwrap(), sha256_text("one"));
    cache.save().unwrap();
    // 0600, because the cache sits beside the controller's receipts and inherits their rule rather than the
    // umask's opinion.
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = fs::symlink_metadata(&cache_path).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600);
    }

    let poisoned = "a".repeat(64);
    let mut entries: serde_json::Map<String, Value> = serde_json::from_slice(&fs::read(&cache_path).unwrap()).unwrap();
    entries[jar.to_str().unwrap()]["sha256"] = json!(poisoned);
    fs::write(&cache_path, serde_json::to_vec(&entries).unwrap()).unwrap();

    let mut reopened = FileDigestCache::open(&cache_path);
    assert_eq!(reopened.digest(&jar).unwrap(), poisoned);
    // A file whose bytes moved is re-hashed rather than believed.
    fs::write(&jar, "one and a half").unwrap();
    assert_eq!(reopened.digest(&jar).unwrap(), sha256_text("one and a half"));
}

/// A rebuilt jar of the same length rewritten in place keeps its size and its inode, so the mtime is the only part
/// of the key that moves - and on Windows, with no inode, the only part besides the size at all. The same bytes under
/// the old mtime are believed (which is what proves the other two did not move), and the new mtime alone is re-hashed.
#[test]
fn an_mtime_change_alone_invalidates_a_cache_entry() {
    let root = tempfile::tempdir().unwrap();
    let jar = root.path().join("util.jar");
    fs::write(&jar, "one").unwrap();
    let cached = fs::metadata(&jar).unwrap().modified().unwrap();
    let mut cache = FileDigestCache::open(&root.path().join("daemon-digests.json"));
    assert_eq!(cache.digest(&jar).unwrap(), sha256_text("one"));

    let touch = |time: std::time::SystemTime| {
        File::options().write(true).open(&jar).unwrap().set_modified(time).unwrap();
    };
    fs::write(&jar, "two").unwrap();
    touch(cached);
    assert_eq!(
        cache.digest(&jar).unwrap(),
        sha256_text("one"),
        "size, inode and mtime all held, so the entry stands"
    );
    touch(cached + std::time::Duration::from_secs(1));
    assert_eq!(cache.digest(&jar).unwrap(), sha256_text("two"));
}

/// A cache keyed any other way than exact integer nanoseconds, a float `mtimeMs` for one, is a miss that costs one
/// sweep, never a believed digest, and the sweep leaves a cache that the next run consults.
#[test]
fn a_cache_in_another_format_is_a_miss_and_is_rewritten() {
    let root = tempfile::tempdir().unwrap();
    let jar = root.path().join("util.jar");
    fs::write(&jar, "bytes").unwrap();
    let key = StatKey::of(&fs::metadata(&jar).unwrap());
    let poisoned = "b".repeat(64);
    let written = format!(
        r#"{{{}:{{"size":{},"mtimeMs":{},"ino":{},"sha256":"{poisoned}"}}}}"#,
        serde_json::to_string(jar.to_str().unwrap()).unwrap(),
        key.size,
        key.mtime_ns / 1_000_000,
        key.ino,
    );
    let cache_path = root.path().join("digests.json");
    fs::write(&cache_path, written).unwrap();
    let mut cache = FileDigestCache::open(&cache_path);
    assert_eq!(cache.digest(&jar).unwrap(), sha256_text("bytes"));
    cache.save().unwrap();

    let entries: serde_json::Map<String, Value> = serde_json::from_slice(&fs::read(&cache_path).unwrap()).unwrap();
    let entry = &entries[jar.to_str().unwrap()];
    assert_eq!(entry["mtimeNs"], json!(key.mtime_ns));
    assert!(entry.get("mtimeMs").is_none(), "{entry}");
}

/// Every nanosecond mtime survives the cache file exactly, so a warm run re-hashes nothing it hashed before.
#[test]
fn the_stat_key_round_trips_through_the_cache_file_exactly() {
    // Nanosecond mtimes near the present, the range a float millisecond key misread about one time in nine.
    let base: u64 = 1_758_000_000_000_000_000;
    let entries: BTreeMap<String, CacheEntry> = (0..10_000u64)
        .map(|index| {
            let mtime_ns = base + index.wrapping_mul(2_654_435_761) % 1_000_000_000_000;
            (
                format!("/jar/{index}"),
                CacheEntry {
                    size: index,
                    mtime_ns,
                    ino: index,
                    sha256: String::new(),
                },
            )
        })
        .collect();
    let decoded: BTreeMap<String, CacheEntry> = serde_json::from_str(&serde_json::to_string(&entries).unwrap()).unwrap();
    assert_eq!(decoded, entries);
}

/// A truncated cache costs a sweep, never a run.
#[test]
fn an_unreadable_cache_is_empty_rather_than_fatal() {
    let root = tempfile::tempdir().unwrap();
    let cache_path = root.path().join("digests.json");
    fs::write(&cache_path, r#"{"/a/b.jar":{"size":1,"#).unwrap();
    let jar = root.path().join("jar");
    fs::write(&jar, "bytes").unwrap();
    assert_eq!(FileDigestCache::open(&cache_path).digest(&jar).unwrap(), sha256_text("bytes"));
}

#[cfg(unix)]
fn chmod(path: &Path, mode: u32) -> io::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
}

#[cfg(unix)]
#[test]
fn directory_digests_track_contents_names_and_executable_bits() {
    let root = tempfile::tempdir().unwrap();
    let input = root.path().join("package");
    fs::create_dir_all(input.join("dist")).unwrap();
    chmod(&input, 0o755).unwrap();
    chmod(&input.join("dist"), 0o755).unwrap();
    let entry = input.join("dist").join("cli.js");
    fs::write(&entry, "initial").unwrap();
    let mut cache = FileDigestCache::open(&root.path().join("cache.json"));
    let mut previous = cache.digest(&input).unwrap();
    assert_eq!(cache.digest(&input).unwrap(), previous, "an unchanged directory changed its digest");
    let renamed = input.join("dist").join("cli.js.renamed");
    let empty = input.join("empty");
    type Mutation<'a> = Box<dyn Fn() -> io::Result<()> + 'a>;
    let mutations: Vec<(&str, Mutation<'_>)> = vec![
        ("content", Box::new(|| fs::write(&entry, "changed content"))),
        ("file mode", Box::new(|| chmod(&entry, 0o700))),
        ("name", Box::new(|| fs::rename(&entry, &renamed))),
        ("removal", Box::new(|| fs::remove_file(&renamed))),
        (
            "new directory",
            Box::new(|| fs::create_dir(&empty).and_then(|()| chmod(&empty, 0o755))),
        ),
        ("directory mode", Box::new(|| chmod(&empty, 0o700))),
        ("root mode", Box::new(|| chmod(&input, 0o700))),
    ];
    for (name, mutate) in mutations {
        mutate().unwrap();
        let current = cache.digest(&input).unwrap();
        assert_ne!(current, previous, "a changed {name} kept the digest");
        previous = current;
    }
}

#[cfg(unix)]
#[test]
fn directory_digests_resolve_the_root_link_and_record_nested_links() {
    use std::os::unix::fs::symlink;
    let root = tempfile::tempdir().unwrap();
    let input = root.path().join("package");
    fs::create_dir(&input).unwrap();
    let link = input.join("link");
    symlink(".", &link).unwrap();
    let alias = root.path().join("alias");
    symlink(&input, &alias).unwrap();
    let mut cache = FileDigestCache::open(&root.path().join("cache.json"));
    let original = cache.digest(&input).unwrap();
    assert_eq!(cache.digest(&alias).unwrap(), original, "the root link changed the identity");
    fs::remove_file(&link).unwrap();
    symlink("missing", &link).unwrap();
    assert_ne!(
        cache.digest(&input).unwrap(),
        original,
        "the new link target did not change the identity"
    );
}

#[test]
fn the_cache_is_not_rewritten_when_nothing_was_hashed() {
    let root = tempfile::tempdir().unwrap();
    let cache_path = root.path().join("digests.json");
    FileDigestCache::open(&cache_path).save().unwrap();
    assert!(
        fs::symlink_metadata(&cache_path).is_err(),
        "a cache that hashed nothing still wrote a file"
    );
}
