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
