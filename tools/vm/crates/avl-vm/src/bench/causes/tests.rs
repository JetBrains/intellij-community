use std::collections::HashMap;

use pretty_assertions::assert_eq;

use super::{Cause, CauseRecord, Loaded, NO_FRAME, NO_STACK, cause, method_of, parse, rows};
use crate::bench::pluginlog::{self, Owner, PluginLog};

fn owners() -> PluginLog {
    pluginlog::parse(
        "a.Activity [sub = a.llm.xml] a bg 1\n\
         a.Service [sub = a.llm.xml] a bg 1\n\
         a.Service$1 [sub = a.llm.xml] a bg 1\n\
         a.Loaded [sub = a.llm.xml] a bg 1\n\
         a.Other [sub = a.composer.xml] a bg 1\n",
    )
    .expect("the plugin log")
}

fn llm() -> Owner {
    Owner {
        plugin: "a".to_owned(),
        module: Some("a.llm".to_owned()),
    }
}

fn frames(frames: &[&str]) -> Vec<String> {
    frames.iter().map(|frame| (*frame).to_owned()).collect()
}

/// The records of two threads interleave, and the reader joins each frame to the record of its thread. A record ends
/// at the next header of its thread. A `- locked` line and a record that `keep` refuses add nothing.
#[test]
fn the_reader_joins_the_frames_of_a_record_by_its_thread() {
    let log = "\
[1.000s][11 ][class,load,cause] Java stack when loading a.First:
[1.000s][22 ][class,load,cause] Java stack when loading b.Skipped:
[1.000s][11 ][class,load,cause] \tat java.lang.ClassLoader.defineClass1(java.base@25/Native Method)
[1.000s][22 ][class,load,cause] \tat b.Caller.run(Caller.kt:1)
[1.000s][11 ][class,load,cause] \t- locked <0x00000079d181cf28> (a java.lang.Object)
[1.000s][11 ][class,load,cause] \tat app//a.Caller.run(Caller.kt:3)
[1.002s][11 ][class,load,cause] Java stack when loading a.Second:
[1.002s][11 ][class,load,cause] \tat app//a.Second.init(Second.kt:9)
";
    let mut records = Vec::new();
    parse(log.as_bytes(), |class| class.starts_with("a."), |record| records.push(record)).expect("the log");
    assert_eq!(
        records,
        vec![
            CauseRecord {
                uptime_ms: 1000.0,
                class: "a.First".to_owned(),
                frames: frames(&[
                    "java.lang.ClassLoader.defineClass1(java.base@25/Native Method)",
                    "app//a.Caller.run(Caller.kt:3)",
                ]),
            },
            CauseRecord {
                uptime_ms: 1002.0,
                class: "a.Second".to_owned(),
                frames: frames(&["app//a.Second.init(Second.kt:9)"]),
            },
        ]
    );
}

#[test]
fn a_line_without_decorators_is_an_error_that_names_it() {
    let error = parse(&b"no decorators\n"[..], |_| true, |_| {}).expect_err("an error");
    assert!(format!("{error:#}").contains("line 1: no decorators"), "{error:#}");
}

/// The entry frame is the frame of the owner nearest to the bottom of the stack: the first code of the loader that
/// the thread ran. The caller is the nearest frame below it that the search does not skip.
#[test]
fn the_entry_is_the_frame_of_the_owner_nearest_to_the_bottom() {
    let stack = frames(&[
        "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)",
        "app//a.Service.connect(Service.kt:40)",
        "com.intellij.serviceContainer.ComponentManagerImpl.getService(ComponentManagerImpl.kt:1)",
        "app//a.Activity.execute(Activity.kt:12)",
        "kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)",
        "com.intellij.ide.startup.impl.StartupManagerImpl.runActivity(StartupManagerImpl.kt:5)",
    ]);
    assert_eq!(
        cause(&llm(), &stack, &owners()),
        Some(Cause {
            entry: "a.Activity.execute".to_owned(),
            caller: Some("com.intellij.ide.startup.impl.StartupManagerImpl.runActivity".to_owned()),
        })
    );
}

/// A coroutine of the owner starts on a thread of the dispatcher, so its body is the entry frame, and the frames of
/// the runtime below it give no caller.
#[test]
fn the_body_of_a_coroutine_is_the_entry_of_its_classes() {
    let stack = frames(&[
        "app//a.Service$1.invokeSuspend(Service.kt:50)",
        "kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)",
        "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:1)",
    ]);
    assert_eq!(
        cause(&llm(), &stack, &owners()),
        Some(Cause {
            entry: "a.Service$1.invokeSuspend".to_owned(),
            caller: None,
        })
    );
}

/// A class that code of another owner loads has the trigger frame of `triggers` as its entry, here the frame of
/// another module of the same plugin, and no caller.
#[test]
fn a_stack_without_a_frame_of_the_owner_has_the_trigger_frame() {
    let stack = frames(&[
        "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)",
        "app//a.Other$$Lambda/0x0000000801001000.invoke(Unknown Source)",
        "app//a.Other.show(Other.kt:7)",
    ]);
    assert_eq!(
        cause(&llm(), &stack, &owners()),
        Some(Cause {
            entry: "a.Other$$Lambda.invoke".to_owned(),
            caller: None,
        })
    );
    assert_eq!(cause(&llm(), &frames(&["java.lang.Thread.run(Thread.java:1)"]), &owners()), None);
}

#[test]
fn a_frame_name_keeps_its_class_and_method_without_the_address_of_a_hidden_class() {
    assert_eq!(method_of("a.Service.connect(Service.kt:40)"), "a.Service.connect");
    assert_eq!(
        method_of("a.Other$$Lambda/0x0000000801001000.invoke(Unknown Source)"),
        "a.Other$$Lambda.invoke"
    );
    assert_eq!(method_of("a.Plain"), "a.Plain");
}

/// The rows group the classes by their entry frame, the most classes first. A row names its most common caller, its
/// loaders and its first classes. A class without a record is in the row of no stack, and a record without a frame
/// of an owner is in its own row.
#[test]
fn the_rows_group_the_classes_by_their_entry() {
    let owner = llm();
    let other = Owner {
        plugin: "a".to_owned(),
        module: Some("a.composer".to_owned()),
    };
    let classes = [
        Loaded {
            owner: &owner,
            name: "a.One",
            ms: 10.0,
        },
        Loaded {
            owner: &owner,
            name: "a.Two",
            ms: 11.0,
        },
        Loaded {
            owner: &other,
            name: "a.Three",
            ms: 12.0,
        },
        Loaded {
            owner: &owner,
            name: "a.Four",
            ms: 13.0,
        },
        Loaded {
            owner: &owner,
            name: "a.Five",
            ms: 14.0,
        },
    ];
    let entry = |caller: &str| {
        Some(Cause {
            entry: "a.Activity.execute".to_owned(),
            caller: Some(caller.to_owned()),
        })
    };
    let causes: HashMap<String, Option<Cause>> = [
        ("a.One".to_owned(), entry("p.Startup.run")),
        ("a.Two".to_owned(), entry("p.Startup.run")),
        ("a.Three".to_owned(), entry("p.Other.run")),
        ("a.Four".to_owned(), None),
    ]
    .into_iter()
    .collect();
    let (rows, total) = rows(&classes, &causes, NO_STACK, 10, 2);
    assert_eq!(total, 3);
    let summary: Vec<_> = rows
        .into_iter()
        .map(|row| (row.entry, row.caller, row.classes, row.first_ms, row.loaders, row.first_classes))
        .collect();
    assert_eq!(
        summary,
        [
            (
                "a.Activity.execute".to_owned(),
                Some("p.Startup.run".to_owned()),
                3,
                10.0,
                vec!["a.llm (a)".to_owned(), "a.composer (a)".to_owned()],
                vec!["a.One".to_owned(), "a.Two".to_owned()],
            ),
            (
                NO_FRAME.to_owned(),
                None,
                1,
                13.0,
                vec!["a.llm (a)".to_owned()],
                vec!["a.Four".to_owned()]
            ),
            (
                NO_STACK.to_owned(),
                None,
                1,
                14.0,
                vec!["a.llm (a)".to_owned()],
                vec!["a.Five".to_owned()]
            ),
        ]
    );
}
