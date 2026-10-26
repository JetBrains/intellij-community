"""The rule that assembles one x86_64 source of libaom with nasm, for `aom.BUILD.bazel`."""

def _nasm_object_impl(ctx):
    src = ctx.file.src
    root = src.owner.workspace_root or "."

    # The assembly includes `config/aom_config.asm`, so the include directory is the parent of `config/`.
    config = ctx.file.config.dirname.rpartition("/")[0]
    args = ctx.actions.args()
    args.add("-f", ctx.attr.format)

    # `-I` takes a directory with its trailing slash.
    args.add("-I" + root + "/")
    args.add("-I" + config + "/")
    args.add("-o", ctx.outputs.out)
    args.add(src)
    ctx.actions.run(
        executable = ctx.executable._nasm,
        arguments = [args],
        inputs = [src, ctx.file.config] + ctx.files.includes,
        outputs = [ctx.outputs.out],
        mnemonic = "Nasm",
        progress_message = "Assembling %{input}",
    )
    return [DefaultInfo(files = depset([ctx.outputs.out]))]

# The rule runs nasm without a shell, so it also builds on a Windows host, where the genrule shell is absent.
nasm_object = rule(
    implementation = _nasm_object_impl,
    attrs = {
        "config": attr.label(allow_single_file = [".asm"], mandatory = True),
        "format": attr.string(mandatory = True),
        "includes": attr.label_list(allow_files = [".asm"]),
        "out": attr.output(mandatory = True),
        "src": attr.label(allow_single_file = [".asm"], mandatory = True),
        "_nasm": attr.label(default = Label("@nasm//:nasm"), executable = True, cfg = "exec"),
    },
)
