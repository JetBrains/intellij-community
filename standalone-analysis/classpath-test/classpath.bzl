"""Supplies the Bazel runtime jars for a Standalone layer."""

load("@rules_java//java:defs.bzl", "JavaInfo")

def _classpath_impl(ctx):
    jars = ctx.attr.layer[JavaInfo].transitive_runtime_jars.to_list()
    lower_jars = {
        jar: True
        for layer in ctx.attr.lower_layers
        for jar in layer[JavaInfo].transitive_runtime_jars.to_list()
    }
    manifest = ctx.actions.declare_file(ctx.label.name + ".txt")
    ctx.actions.write(manifest, "\n".join([
        "%s %s" % (
            "-libraryjars" if jar.is_source or jar in lower_jars else "-injars",
            jar.short_path,
        )
        for jar in sorted(jars, key = str)
    ]) + "\n")
    return [DefaultInfo(files = depset([manifest]), runfiles = ctx.runfiles(files = jars))]

standalone_classpath = rule(
    implementation = _classpath_impl,
    attrs = {
        "layer": attr.label(mandatory = True, providers = [JavaInfo]),
        "lower_layers": attr.label_list(providers = [JavaInfo]),
    },
)

def _owner(jar):
    """The label of the target that builds a module jar, for example `//platform/util:util`, or `-` for a library jar.

    The label has no repository, so the golden is the same from the community and the ultimate roots.
    No target builds a library jar, because Bazel downloads it, so the test names it by its file name.
    """
    if jar.is_source:
        return "-"
    return "//%s:%s" % (jar.owner.package, jar.owner.name)

def _jars_impl(ctx):
    lines = []
    files = []
    for index, layer in enumerate([ctx.attr.layer] + ctx.attr.lower_layers):
        group = "layer" if index == 0 else "lower%d" % index
        for jar in sorted(layer[JavaInfo].transitive_runtime_jars.to_list(), key = str):
            files.append(jar)
            lines.append("%s %s %s" % (group, _owner(jar), jar.short_path))
    manifest = ctx.actions.declare_file(ctx.label.name + ".txt")
    ctx.actions.write(manifest, "\n".join(lines) + "\n")
    return [DefaultInfo(files = depset([manifest]), runfiles = ctx.runfiles(files = files))]

standalone_jars = rule(
    doc = "Lists the runtime jars of a Standalone layer and of each lower layer.",
    implementation = _jars_impl,
    attrs = {
        "layer": attr.label(mandatory = True, providers = [JavaInfo]),
        "lower_layers": attr.label_list(providers = [JavaInfo]),
    },
)
