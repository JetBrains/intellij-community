use super::*;

/// The trailing dot in the `v24.` prefix is the whole major check. The patch level is free.
#[test]
fn the_node_major_is_matched_as_a_whole_component() {
    let cases = [
        ("v24.10.0", true),
        ("v24.0.0", true),
        ("v24.11.2", true),
        // A prefix without the dot accepts this one.
        ("v240.1.0", false),
        ("v2.4.0", false),
        ("v18.19.1", false),
        ("v24", false),
    ];
    for (version, accepted) in cases {
        assert_eq!(is_node_major(version, 24), accepted, "{version}");
    }
}
