use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::{ClassLoadLog, LoadedClass, Source, SourceCounts, by_module, by_plugin, parse, platform_count};
use crate::bench::pluginlog;
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
    assert_eq!(log.named().count(), 12);
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
    assert_eq!(
        by_module(&log, &owners, None),
        counts(&[("q.m", 1)]),
        "a class of a main module has no content module"
    );
    assert_eq!(by_module(&log, &owners, Some(4000.0)), counts(&[]));
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
