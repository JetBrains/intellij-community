use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::{ClassLoadLog, LoadedClass, Source, SourceCounts, by_loader, by_plugin, first_loads, parse, platform_count};
use crate::bench::pluginlog::{self, Owner, Tally, Thread};
use crate::bench::testing::testdata_dir;

fn fixture(name: &str) -> String {
    let path = testdata_dir().join("session/non-modal-run-01").join(name);
    std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

fn class(uptime_ms: f64, name: &str, source: Source) -> LoadedClass {
    LoadedClass {
        uptime_ms,
        name: name.to_owned(),
        source,
        tid: None,
    }
}

fn counts(pairs: &[(&str, u64)]) -> BTreeMap<String, u64> {
    pairs.iter().map(|(name, count)| ((*name).to_owned(), *count)).collect()
}

#[test]
fn reads_every_line_shape() {
    let log = parse(
        "[0.010s][class,load] opened: /jbr/lib/modules\n\
         [0.050s][class,load] java.lang.Object source: shared objects file\n\
         [0.070s][class,load] java.util.HashMap source: shared objects file (top)\n\
         [100ms][info][class,load] jdk.internal.module.ModuleBootstrap source: jrt:/java.base\n\
         [12.345s][class,load] a.Boot source: instance of java.lang.Class\n\
         [9.997s ][class,load] a.Padded source: jrt:/java.net.http\n\
         [1234.567s][class,load] org.foo.Bar source: file:/path/to/lib/x.jar\n\
         [900000000ns][class,load] org.foo.Baz source: jar:file:/path/to/lib/y.jar!/\n\
         [1.000s][class,load] com.intellij.Foo source: com.intellij.util.lang.PathClassLoader @1a2b3c\n\
         [1.500s][info][class,load] com.intellij.Bar$1 source: com.intellij.ide.plugins.cl.PluginClassLoader\n\
         [2.000s][class,load] java.lang.invoke.LambdaForm$MH/0x0000000801001000 source: __JVM_LookupDefineClass__\n\
         [2.100s][class,load] com.intellij.Foo$$Lambda/0x0000000801002000 source: com.intellij.Foo\n\
         [2.200s][class,load] com.intellij.Qux source: __JVM_DefineClass__\n\
         [2.300s][class,load] jdk.proxy2.$Proxy7 source: __dynamic_proxy__\n\
         [2.400s][class,load] com.intellij.Boot source: /Users/a/Application Support/dist/lib/boot.jar\n\
         [2.500s][class,load] com.intellij.Win source: C:\\dist\\lib\\boot.jar\n",
    )
    .expect("a class-load log");
    assert_eq!(
        log.classes,
        vec![
            class(50.0, "java.lang.Object", Source::Jdk),
            class(70.0, "java.util.HashMap", Source::Jdk),
            class(100.0, "jdk.internal.module.ModuleBootstrap", Source::Jdk),
            class(12345.0, "a.Boot", Source::Jdk),
            class(9997.0, "a.Padded", Source::Jdk),
            class(1_234_567.0, "org.foo.Bar", Source::Jar("file:/path/to/lib/x.jar".to_owned())),
            class(900.0, "org.foo.Baz", Source::Jar("jar:file:/path/to/lib/y.jar!/".to_owned())),
            class(
                1000.0,
                "com.intellij.Foo",
                Source::Loader("com.intellij.util.lang.PathClassLoader".to_owned())
            ),
            class(
                1500.0,
                "com.intellij.Bar$1",
                Source::Loader("com.intellij.ide.plugins.cl.PluginClassLoader".to_owned())
            ),
            class(2000.0, "java.lang.invoke.LambdaForm$MH/0x0000000801001000", Source::Hidden),
            class(2100.0, "com.intellij.Foo$$Lambda/0x0000000801002000", Source::Hidden),
            class(2200.0, "com.intellij.Qux", Source::Loader("__JVM_DefineClass__".to_owned())),
            class(2300.0, "jdk.proxy2.$Proxy7", Source::Loader("__dynamic_proxy__".to_owned())),
            class(
                2400.0,
                "com.intellij.Boot",
                Source::Jar("/Users/a/Application Support/dist/lib/boot.jar".to_owned())
            ),
            class(2500.0, "com.intellij.Win", Source::Jar("C:\\dist\\lib\\boot.jar".to_owned())),
        ]
    );
    assert_eq!(log.named().count(), 13);
}

#[test]
fn counts_the_fixture_by_source_kind_and_before_a_bound() {
    let log = parse(&fixture("class-load.log")).expect("a class-load log");
    assert_eq!(log.classes.len(), 37);
    assert_eq!(
        log.by_source_kind(),
        SourceCounts {
            jdk: 12,
            jar: 4,
            loader: 16,
            hidden: 5
        }
    );
    assert_eq!(log.count_before(1000.0), 17);
    assert_eq!(log.count_before(0.0), 0);
    assert_eq!(log.count_before(f64::INFINITY), 32);
}

#[test]
fn joins_the_fixture_with_the_plugin_log() {
    let log = parse(&fixture("class-load.log")).expect("a class-load log");
    let owners = pluginlog::parse(&fixture("plugin-classes.txt")).expect("a plugin log");
    assert_eq!(
        by_plugin(&log, &owners, None),
        counts(&[
            ("com.intellij", 4),
            ("com.intellij.dfa.analysis", 2),
            ("com.jetbrains.gateway", 1),
            ("com.jetbrains.performancePlugin", 2),
            ("com.jetbrains.station", 1),
            ("intellij.webp", 1),
            ("org.jetbrains.plugins.docker.gateway", 1),
        ])
    );
    assert_eq!(
        by_plugin(&log, &owners, Some(2050.0)),
        counts(&[("com.intellij", 4), ("com.jetbrains.performancePlugin", 2)])
    );
    assert_eq!(platform_count(&log, &owners, None), 4);
    assert_eq!(platform_count(&log, &owners, Some(1000.0)), 1);
}

#[test]
fn joins_only_named_classes_with_an_owner() {
    let log = parse(
        "[1s][class,load] a.A source: com.intellij.ide.plugins.cl.PluginClassLoader\n\
         [2s][class,load] a.A$$Lambda/0x01 source: a.A\n\
         [3s][class,load] b.B source: com.intellij.util.lang.PathClassLoader\n\
         [4s][class,load] c.C source: file:/lib/c.jar\n",
    )
    .expect("a class-load log");
    let owners = pluginlog::parse("a.A [m] p\na.A$$Lambda/0x01 [m] p\nc.C [sub = q.m.xml] q\n").expect("a plugin log");
    assert_eq!(by_plugin(&log, &owners, None), counts(&[("p", 1), ("q", 1)]));
    let loader = |plugin: &str, module: Option<&str>| Owner {
        plugin: plugin.to_owned(),
        module: module.map(str::to_owned),
    };
    let one = Tally {
        classes: 1,
        ..Tally::default()
    };
    assert_eq!(
        by_loader(&log, &owners, None),
        BTreeMap::from([(loader("p", None), one), (loader("q", Some("q.m")), one)]),
        "the main module of a plugin is a loader, and a hidden class counts for none"
    );
    assert_eq!(by_loader(&log, &owners, Some(4000.0)), BTreeMap::from([(loader("p", None), one)]));
    assert_eq!(
        first_loads(&log, &owners),
        BTreeMap::from([(loader("p", None), 1000.0), (loader("q", Some("q.m")), 4000.0)])
    );
    assert_eq!(platform_count(&log, &owners, None), 1);
    assert_eq!(platform_count(&ClassLoadLog::default(), &owners, None), 0);
}

#[test]
fn refuses_a_line_of_another_shape_by_its_number() {
    let refusal = |text: &str| format!("{:#}", parse(text).expect_err("a refusal"));
    assert_eq!(
        refusal("[0.050s][class,load] a.A source: shared objects file\n\n[0.1s][class,load] b.B\n"),
        "line 3: [0.1s][class,load] b.B: no ` source: ` after the class name"
    );
    assert_eq!(refusal("a.A source: x\n"), "line 1: a.A source: x: no `[<uptime>]` at the start");
    assert_eq!(
        refusal("[0.1m][class,load] a.A source: x\n"),
        "line 1: [0.1m][class,load] a.A source: x: the uptime `0.1m` has no unit s, ms or ns"
    );
    assert_eq!(
        refusal("[x.ys][class,load] a.A source: x\n"),
        "line 1: [x.ys][class,load] a.A source: x: the uptime `x.ys` is not a number: invalid float literal"
    );
    assert_eq!(
        refusal("[0.1s][class,load a.A source: x\n"),
        "line 1: [0.1s][class,load a.A source: x: no `]` after a decorator"
    );
    assert_eq!(
        refusal("[0.1s][class,load]a.A source: x\n"),
        "line 1: [0.1s][class,load]a.A source: x: no space after the decorators"
    );
    assert_eq!(
        refusal("[0.1s][class,load] a.A source: \n"),
        "line 1: [0.1s][class,load] a.A source: : the source is empty"
    );
}

/// A log of the `uptime,tid,tags` decorators: the JVM pads the tid and the tags, and the reader trims them. A Java
/// thread starts as `Unknown thread`, and the dump or its `exiting` line names it. A class has a thread only when the
/// EDT is known.
#[test]
fn the_tid_of_a_class_and_the_thread_dump_name_the_edt() {
    let text = "[0.007s][4355][os,thread] Thread attached (tid: 4355, pthread id: 6163492864, stack: 0x1 - 0x2 (2060K) ).\n\
         [0.010s][4355 ][os,thread] Thread \"GC Thread#0\" started (pthread id: 6165639168, attributes: stacksize: 2048k). \n\
         [0.010s][12291][os,thread] Thread is alive (tid: 12291, pthread id: 6165639168).\n\
         [0.020s][4355 ][os,thread] Thread \"Unknown thread\" started (pthread id: 6167785472, attributes: stacksize: 2048k). \n\
         [0.020s][20000][os,thread] Thread is alive (tid: 20000, pthread id: 6167785472).\n\
         [0.060s][4355 ][class,load      ] java.lang.Float source: shared objects file\n\
         [0.070s][20000][class,load] a.A source: __JVM_DefineClass__\n\
         [0.080s][30000][class,load] b.B source: __JVM_DefineClass__\n\
         [0.090s][30000][os,thread ] JavaThread exiting (name: \"pool-1\", tid: 30000).\n";
    let mut log = parse(text).expect("a class-load log");
    assert_eq!(
        log.classes.iter().map(|class| (class.name.as_str(), class.tid)).collect::<Vec<_>>(),
        [("java.lang.Float", Some(4355)), ("a.A", Some(20000)), ("b.B", Some(30000))]
    );
    assert!(!log.threads.knows_edt(), "a Java thread starts without its name");
    assert_eq!(log.threads.thread(Some(20000)), None, "without the EDT no class has a thread");
    assert_eq!(log.edt_count_before(f64::INFINITY), None);

    log.threads.add_dump(
        "51234:\n\"main\" #1 [4355] prio=5 os_prio=31 cpu=812.41ms tid=0x1 nid=4355 waiting on condition\n\
         \"AWT-EventQueue-0\" #34 [20000] prio=6 os_prio=31 tid=0x2 nid=20000 waiting on condition  [0x3]\n\
         \"AWT-EventQueue-1\" os_prio=31 tid=0x4 nid=0x7530 runnable\n\
         JNI global refs: 52, weak refs: 0\n",
    );
    assert_eq!(log.threads.thread(Some(20000)), Some(Thread::Edt));
    assert_eq!(log.threads.thread(Some(4355)), Some(Thread::Background));
    assert_eq!(log.threads.thread(None), None, "a log without the tid gives no thread");
    assert_eq!(
        log.threads.thread(Some(30000)),
        Some(Thread::Edt),
        "a hex nid works, and the dump names the thread that the exiting line named pool-1"
    );
    assert_eq!(log.edt_count_before(f64::INFINITY), Some(2));
    assert_eq!(log.edt_count_before(65.0), Some(0));
}
