use pretty_assertions::assert_eq;

use super::{ClassCounts, Count, parse};

#[test]
fn counts_classes_per_plugin_and_per_content_module() {
    let counts = parse(
        "a.A [m] com.intellij\n\
         b.B [sub = intellij.platform.settings.local.xml] com.intellij\n\
         c.C [sub = intellij.lombok.xml] Lombook Plugin\n\
         d.D [m] org.jetbrains.plugins.yaml:yaml\n",
    )
    .expect("a class log");
    assert_eq!(counts.total, 4);
    assert_eq!(
        ClassCounts::top(&counts.by_plugin, 2),
        vec![
            Count {
                name: "com.intellij".to_owned(),
                classes: 2
            },
            Count {
                name: "Lombook Plugin".to_owned(),
                classes: 1
            },
        ]
    );
    assert_eq!(counts.by_plugin.get("org.jetbrains.plugins.yaml"), Some(&1));
    assert_eq!(counts.by_module.len(), 2);
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
