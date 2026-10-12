use pretty_assertions::assert_eq;

use super::{EdtProfile, ExcludedSamples, FrameSamples, ROOT_TRIGGER, parse};

/// The stacks of the profiler's own symbol parsing, under its dlopen hook, count for neither the EDT frames nor the
/// class-loading triggers nor the share of the define samples. The profile counts them apart, on every thread and on
/// the EDT, and a merge adds them up.
#[test]
fn leaves_out_the_symbol_parsing_of_the_profiler() {
    let profile = parse(
        "[AWT-EventQueue-0 tid=1];a/A.run;java/lang/System.loadLibrary;jdk/internal/loader/NativeLibraries.load;dlopen;\
         Profiler::dlopen_hook(char const*, int);Symbols::parseLibraries;MachOParser::parse 480\n\
         [AWT-EventQueue-0 tid=1];a/A.run;b/B.paint 20\n\
         [DefaultDispatcher-worker-1 tid=7];c/C.run;java/lang/ClassLoader.defineClass1;JVM_DefineClass;\
         jdk/internal/loader/NativeLibraries.load;Profiler::dlopen_hook;CodeCache::sort 30\n\
         [DefaultDispatcher-worker-1 tid=7];c/C.run;d/D.init;java/lang/ClassLoader.defineClass1 5\n\
         [DefaultDispatcher-worker-1 tid=7];c/C.run;my_dlopen_hook_helper 3\n",
    )
    .expect("a profile");
    assert_eq!((profile.samples, profile.all_samples), (20, 28));
    assert_eq!(
        profile.top(5),
        [FrameSamples {
            frame: "b/B.paint".to_owned(),
            samples: 20
        }]
    );
    assert_eq!(profile.triggers.define_samples, 5);
    assert_eq!(
        profile.triggers.top(5),
        [FrameSamples {
            frame: "d/D.init".to_owned(),
            samples: 5
        }]
    );
    assert_eq!(
        profile.excluded,
        ExcludedSamples {
            samples: 510,
            edt_samples: 480
        },
        "a frame that only holds the name is no hook"
    );
    let mut total = profile.clone();
    total.merge(&profile);
    assert_eq!(
        total.excluded,
        ExcludedSamples {
            samples: 1020,
            edt_samples: 960
        }
    );
}

#[test]
fn charges_the_edt_samples_to_the_deepest_java_frame() {
    let profile = parse(
        "[AWT-EventQueue-0 tid=123];a/A.run;b/B.paint;__psynch_mutexwait 5\n\
         [AWT-EventQueue-0 tid=123];a/A.run;c/C.layout 3\n\
         [AWT-EventQueue-0 tid=123];x/X.run;b/B.paint 2\n\
         [AWT-EventQueue-0 tid=123];thread_start;swtch_pri 1\n\
         [DefaultDispatcher-worker-1 tid=7];a/A.run;b/B.paint 40\n",
    )
    .expect("a profile");
    assert_eq!(profile.samples, 11);
    assert_eq!(profile.all_samples, 51);
    assert_eq!(
        profile.top(2),
        vec![
            FrameSamples {
                frame: "b/B.paint".to_owned(),
                samples: 7
            },
            FrameSamples {
                frame: "c/C.layout".to_owned(),
                samples: 3
            },
        ]
    );
    assert_eq!(profile.self_samples.get("swtch_pri"), Some(&1));
}

#[test]
fn merges_profiles() {
    let mut total = EdtProfile::default();
    total.merge(&parse("[AWT-EventQueue-0 tid=1];a/A.a 1\n").expect("a profile"));
    total.merge(&parse("[AWT-EventQueue-0 tid=2];a/A.a 2\n").expect("a profile"));
    assert_eq!(total.samples, 3);
    assert_eq!(total.self_samples.get("a/A.a"), Some(&3));
}

#[test]
fn charges_the_define_samples_of_every_thread_to_the_nearest_trigger() {
    let profile = parse(
        "[AWT-EventQueue-0 tid=1];a/A.run;b/B.init;java/lang/ClassLoader.loadClass;\
         com/intellij/util/lang/PathClassLoader.loadClass;java/lang/ClassLoader.defineClass1;\
         SystemDictionary::resolve_from_stream 5\n\
         [DefaultDispatcher-worker-1 tid=7];c/C.run;d/D.get;InterpreterRuntime::_new;JavaCalls::call_helper;\
         com/intellij/ide/plugins/cl/PluginClassLoader.loadClass;java/lang/ClassLoader.defineClass1;\
         JVM_DefineClass;SystemDictionary::resolve_super_or_fail;java/lang/ClassLoader.loadClass;\
         java/lang/ClassLoader.defineClass1 3\n\
         [main tid=2];e/E.main;java/lang/reflect/Method.invoke;jdk/internal/reflect/DirectMethodHandleAccessor.invoke;\
         java/lang/invoke/MethodHandleNatives.linkCallSite;java/lang/ClassLoader.defineClass0 2\n\
         [main tid=2];thread_start;java/lang/Class.forName;java/lang/ClassLoader.defineClass2 4\n\
         [main tid=2];java/lang/ClassLoader.defineClass1 1\n\
         [main tid=2];e/E.main;b/B.init 10\n",
    )
    .expect("a profile");
    assert_eq!(profile.all_samples, 25);
    assert_eq!(profile.triggers.define_samples, 15);
    assert_eq!(
        profile.triggers.top(3),
        vec![
            FrameSamples {
                frame: "b/B.init".to_owned(),
                samples: 5
            },
            FrameSamples {
                frame: "thread_start".to_owned(),
                samples: 4
            },
            FrameSamples {
                frame: "d/D.get".to_owned(),
                samples: 3
            },
        ]
    );
    assert_eq!(profile.triggers.by_trigger.get("e/E.main"), Some(&2));
    assert_eq!(profile.triggers.by_trigger.get(ROOT_TRIGGER), Some(&1));
    assert_eq!(profile.define_share(), Some(0.6));
    assert_eq!(EdtProfile::default().define_share(), None);
}

#[test]
fn merges_the_triggers() {
    let mut total = EdtProfile::default();
    total.merge(&parse("[main tid=1];a/A.a;java/lang/ClassLoader.defineClass1 1\n").expect("a profile"));
    total.merge(&parse("[main tid=2];a/A.a;java/lang/ClassLoader.defineClass1 2\n").expect("a profile"));
    assert_eq!(total.triggers.define_samples, 3);
    assert_eq!(total.triggers.by_trigger.get("a/A.a"), Some(&3));
}

#[test]
fn refuses_a_line_without_a_count() {
    let error = parse("[AWT-EventQueue-0 tid=1];a x\n").expect_err("a refusal");
    assert_eq!(
        format!("{error:#}"),
        "line 1: the sample count `x` is not a number: invalid digit found in string"
    );
}
