use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::{Owner, parse};

#[test]
fn counts_classes_per_plugin_and_per_content_module() {
    let log = parse(
        "a.A [m] com.intellij\n\
         b.B [sub = intellij.platform.settings.local.xml] com.intellij\n\
         c.C [sub = intellij.lombok.xml] Lombook Plugin\n\
         d.D [m] org.jetbrains.plugins.yaml:yaml\n",
    )
    .expect("a class log");
    let counts = log.counts;
    assert_eq!(counts.total, 4);
    assert_eq!(
        counts.by_plugin,
        [("Lombook Plugin", 1), ("com.intellij", 2), ("org.jetbrains.plugins.yaml", 1)]
            .into_iter()
            .map(|(name, count)| (name.to_owned(), count))
            .collect::<BTreeMap<_, _>>()
    );
    assert_eq!(counts.by_module.len(), 2);
}

#[test]
fn reads_the_owner_of_each_class_and_keeps_the_first_line() {
    let log = parse(
        "a.A [m] com.intellij\n\
         \n\
         b.B$C [sub = intellij.platform.settings.local.xml] com.intellij\n\
         a.A [m] org.jetbrains.plugins.yaml:yaml\n",
    )
    .expect("a class log");
    assert_eq!(log.owners.len(), 2);
    assert_eq!(log.counts.total, 3, "each line counts");
    assert_eq!(
        log.owners.get("a.A"),
        Some(&Owner {
            plugin: "com.intellij".to_owned(),
            module: None
        })
    );
    assert_eq!(
        log.owners.get("b.B$C"),
        Some(&Owner {
            plugin: "com.intellij".to_owned(),
            module: Some("intellij.platform.settings.local".to_owned())
        })
    );
    let error = parse("a.A [m] p\n\nb.B [m] \n").expect_err("a refusal");
    assert_eq!(format!("{error:#}"), "line 3: b.B [m] : no plugin id after the marker");
}

#[test]
fn refuses_a_line_of_another_shape_by_its_number() {
    let error = parse("a.A [m] p\nb.B [x] p\n").expect_err("a refusal");
    assert_eq!(
        format!("{error:#}"),
        "line 2: b.B [x] p: the marker [x] is neither [m] nor [sub = <module>.xml]"
    );
    let error = parse("a.A p\n").expect_err("a refusal");
    assert_eq!(format!("{error:#}"), "line 1: a.A p: no `[` marker after the class name");
}
