use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::{Activation, Define, Owner, PluginLogCost, Tally, Thread, parse};

fn owner(plugin: &str, module: Option<&str>) -> Owner {
    Owner {
        plugin: plugin.to_owned(),
        module: module.map(str::to_owned),
    }
}

#[test]
fn counts_classes_per_plugin_and_per_loader() {
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
    assert_eq!(counts.by_loader.len(), 4, "the main module of a plugin is a loader too");
    assert_eq!(
        counts.by_loader.get(&owner("Lombook Plugin", Some("intellij.lombok"))),
        Some(&Tally {
            classes: 1,
            ..Tally::default()
        }),
        "a log of the old shape times no class"
    );
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
    assert_eq!(log.classes.len(), 2);
    assert_eq!(log.counts.total, 3, "each line counts");
    assert_eq!(log.owner("a.A"), Some(&owner("com.intellij", None)));
    assert_eq!(
        log.owner("b.B$C"),
        Some(&owner("com.intellij", Some("intellij.platform.settings.local")))
    );
    let error = parse("a.A [m] p\n\nb.B [m] \n").expect_err("a refusal");
    assert_eq!(format!("{error:#}"), "line 3: b.B [m] : no plugin id after the marker");
}

/// A record of the new shape has the thread and the nanos after the plugin id. The first class of a loader carries
/// the activation stack, and a record with a define error counts as no class.
#[test]
fn reads_the_thread_the_time_and_the_activation_stack_of_the_new_shape() {
    let log = parse(
        "com.intellij.air.Foo [sub = intellij.air.frontend.prompt.ui.xml] com.intellij.air edt 81234\n\
         \tat com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry.<init>(FrontendRemoteTopicListenersRegistry.kt:69)\n\
         \tat app//com.intellij.serviceContainer.ComponentManagerImpl.instantiate(ComponentManagerImpl.kt:1200)\n\
         com.intellij.air.Bar [sub = intellij.air.frontend.prompt.ui.xml] com.intellij.air bg 10022\n\
         c.C [sub = intellij.lombok.xml] Lombook Plugin bg 7\n\
         d.D [m] org.jetbrains.plugins.yaml:yaml edt 5\n\
         \tat d.Boot.run(Boot.kt:1)\n\
         e.E [m] org.jetbrains.plugins.yaml:yaml bg 3\n\
         \terror: e/E duplicate class definition\n",
    )
    .expect("a class log");
    assert_eq!((log.counts.total, log.errors), (4, 1));
    assert_eq!(log.owner("e.E"), None, "a failed define is no class");
    assert_eq!(
        log.classes.get("com.intellij.air.Foo").map(|class| class.define),
        Some(Some(Define {
            thread: Some(Thread::Edt),
            nanos: 81234
        }))
    );
    let prompt = owner("com.intellij.air", Some("intellij.air.frontend.prompt.ui"));
    assert_eq!(
        log.counts.by_loader.get(&prompt),
        Some(&Tally {
            classes: 2,
            timed: 2,
            edt_classes: 1,
            edt_nanos: 81234,
            bg_nanos: 10022
        })
    );
    assert_eq!(
        log.activations.get(&prompt),
        Some(&Activation {
            first_class: "com.intellij.air.Foo".to_owned(),
            stack: vec![
                "com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry.<init>(FrontendRemoteTopicListenersRegistry.kt:69)"
                    .to_owned(),
                "app//com.intellij.serviceContainer.ComponentManagerImpl.instantiate(ComponentManagerImpl.kt:1200)".to_owned(),
            ]
        })
    );
    assert_eq!(
        log.activations.get(&owner("Lombook Plugin", Some("intellij.lombok"))),
        Some(&Activation {
            first_class: "c.C".to_owned(),
            stack: Vec::new()
        }),
        "a plugin id with a space keeps it"
    );
    assert_eq!(
        log.activations
            .get(&owner("org.jetbrains.plugins.yaml", None))
            .map(|activation| activation.stack.len()),
        Some(1)
    );
}

/// Threads interleave whole records, so a record of another class of a loader can come before the record with the
/// stack. The record with the stack names the first class.
#[test]
fn the_record_with_the_stack_names_the_first_class_of_a_loader() {
    let log = parse(
        "a.Second [m] p bg 1\n\
         a.First [m] p edt 2\n\
         \tat q.Q.run(Q.kt:3)\n",
    )
    .expect("a class log");
    assert_eq!(
        log.activations.get(&owner("p", None)),
        Some(&Activation {
            first_class: "a.First".to_owned(),
            stack: vec!["q.Q.run(Q.kt:3)".to_owned()]
        })
    );
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
    let error = parse("\tat a.A.run(A.kt:1)\n").expect_err("a refusal");
    assert_eq!(
        format!("{error:#}"),
        "line 1: \tat a.A.run(A.kt:1): a frame line before the first class line"
    );
    let log = parse("a.A [m] p ui 12\n").expect("a class log");
    assert_eq!(
        log.owner("a.A"),
        Some(&owner("p ui 12", None)),
        "a token that is not a thread leaves the old shape"
    );
}

/// A v2 log: a header, `L` before the `C` records of its loader, `S` records after other records, an `E` record, a
/// plugin id with a space, and the `#cost` trailer.
#[test]
fn reads_a_v2_log_with_late_stack_records_an_error_and_the_trailer() {
    let log = parse(
        "#plugin-classes\t2\n\
         L\t1\tintellij.air.frontend.prompt.ui\tcom.intellij.air\n\
         C\t1\t81234\tcom.intellij.air.Foo\n\
         L\t2\t-\tLombook Plugin\n\
         C\t2\t7\tc.C\n\
         C\t1\t10022\tcom.intellij.air.Bar\n\
         S\t1\tcom.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry.<init>(FrontendRemoteTopicListenersRegistry.kt:69)\n\
         E\t2\tc.Broken\tc/Broken (wrong name:\tc/Other)\n\
         S\t1\tcom.intellij.serviceContainer.ComponentManagerImpl.instantiate(Unknown Source)\n\
         #cost\t9\t1834000\t1\t912000\n",
    )
    .expect("a class log");
    assert_eq!((log.counts.total, log.errors), (3, 1));
    assert_eq!(log.owner("c.Broken"), None, "a define error is no class");
    let prompt = owner("com.intellij.air", Some("intellij.air.frontend.prompt.ui"));
    let lombok = owner("Lombook Plugin", None);
    assert_eq!(
        log.owner("c.C"),
        Some(&lombok),
        "the plugin id is the last field and keeps its space"
    );
    assert_eq!(
        log.classes.get("com.intellij.air.Foo").map(|class| class.define),
        Some(Some(Define {
            thread: None,
            nanos: 81234
        })),
        "a v2 log names no thread: the JVM class-load log gives it"
    );
    assert_eq!(
        log.counts.by_loader.get(&prompt),
        Some(&Tally {
            classes: 2,
            ..Tally::default()
        }),
        "a define without a thread has no EDT and no BG time"
    );
    assert_eq!(
        log.activations.get(&prompt),
        Some(&Activation {
            first_class: "com.intellij.air.Foo".to_owned(),
            stack: vec![
                "com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry.<init>(FrontendRemoteTopicListenersRegistry.kt:69)"
                    .to_owned(),
                "com.intellij.serviceContainer.ComponentManagerImpl.instantiate(Unknown Source)".to_owned(),
            ]
        }),
        "the S records after other records are the stack of the first class"
    );
    assert_eq!(
        log.activations.get(&lombok),
        Some(&Activation {
            first_class: "c.C".to_owned(),
            stack: Vec::new()
        })
    );
    assert_eq!(
        log.cost,
        Some(PluginLogCost {
            records: 9,
            write_nanos: 1_834_000,
            stacks: 1,
            stack_nanos: 912_000
        })
    );
}

/// The IDE writes the trailer when it closes the log. A log of a killed IDE has no trailer and no cost.
#[test]
fn a_v2_log_without_the_trailer_has_no_cost() {
    let log = parse("\n#plugin-classes\t2\nL\t1\t-\tp\nC\t1\t3\ta.A\n").expect("a class log");
    assert_eq!((log.counts.total, log.cost), (1, None));
    let v1 = parse("a.A [m] p bg 3\n").expect("a class log");
    assert_eq!(v1.cost, None, "a v1 log has no trailer");
}

/// An unknown record kind, a record of another shape, a `C` record before the `L` record of its loader, an `S` record
/// of a loader that never has an `L` record, and a header of another version are errors that name the line.
#[test]
fn a_v2_log_refuses_an_unknown_kind_a_bad_record_and_an_unknown_version() {
    let refusal = |text: &str| format!("{:#}", parse(text).expect_err("a refusal"));
    let header = "#plugin-classes\t2\n";
    assert_eq!(
        refusal(&format!("{header}L\t1\t-\tp\nX\t1\ta.A\n")),
        "line 3: X\t1\ta.A: the record kind `X` is unknown"
    );
    assert_eq!(
        refusal(&format!("{header}C\t1\t3\ta.A\n")),
        "line 2: C\t1\t3\ta.A: the loader 1 has no L record before it"
    );
    assert_eq!(
        refusal(&format!("{header}L\t1\t-\tp\nC\t1\tb\t3\ta.A\n")),
        "line 3: C\t1\tb\t3\ta.A: the nanos `b` is not a whole number: invalid digit found in string",
        "a C record names no thread"
    );
    assert_eq!(
        refusal(&format!("{header}L\t1\t-\tp\nC\t1\t3\n")),
        "line 3: C\t1\t3: the record has fewer than 3 fields after its kind"
    );
    assert_eq!(
        refusal(&format!("{header}L\tone\t-\tp\n")),
        "line 2: L\tone\t-\tp: the loader id `one` is not a whole number: invalid digit found in string"
    );
    assert_eq!(
        refusal(&format!("{header}L\t1\t-\tp\nL\t1\tm\tp\n")),
        "line 3: L\t1\tm\tp: the loader 1 has a second L record"
    );
    assert_eq!(
        refusal(&format!("{header}S\t7\ta.A.run(A.kt:1)\nL\t1\t-\tp\n")),
        "line 2: an S record of the loader 7, which has no L record"
    );
    assert_eq!(
        refusal(&format!("{header}#cost\t1\tmany\t0\t0\n")),
        "line 2: #cost\t1\tmany\t0\t0: the write nanos `many` is not a whole number: invalid digit found in string"
    );
    assert_eq!(
        refusal("#plugin-classes\t3\nL\t1\t-\tp\n"),
        "line 1: #plugin-classes\t3: the version 3 of plugin-classes.txt is unknown; the reader takes version 2"
    );
}

/// The spec of the log requires it: the reader refuses an unknown version, and it reads a log without a header as the
/// old shape. A v2 record without the header is thus a line of the old shape, which the reader refuses.
#[test]
fn the_reader_refuses_an_unknown_version_and_reads_a_log_without_a_header_as_the_old_shape() {
    let refusal = |text: &str| format!("{:#}", parse(text).expect_err("a refusal"));
    assert_eq!(
        refusal("\n#plugin-classes\t1\na.A [m] p\n"),
        "line 2: #plugin-classes\t1: the version 1 of plugin-classes.txt is unknown; the reader takes version 2"
    );
    let old = parse("a.A [m] p\nb.B [sub = q.xml] p bg 3\n").expect("a log of the old shape");
    assert_eq!((old.counts.total, old.cost), (2, None));
    assert_eq!(old.owner("b.B"), Some(&owner("p", Some("q"))));
    assert_eq!(
        refusal("L\t1\t-\tp\nC\t1\t3\ta.A\n"),
        "line 1: L\t1\t-\tp: no `[` marker after the class name",
        "without the header, a v2 record is a line of the old shape"
    );
}
