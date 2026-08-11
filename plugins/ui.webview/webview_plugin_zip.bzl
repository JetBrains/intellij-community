def _single_file(target, attr_name):
    files = target[DefaultInfo].files.to_list()
    if len(files) != 1:
        fail("%s keys must provide exactly one file, got %s from %s" % (attr_name, len(files), target.label))
    return files[0]

def _entry(root_dir, path):
    return root_dir + "/" + path

def _relative_to_package(file, package):
    package_prefix = package + "/"
    short_path = file.short_path
    if short_path.startswith(package_prefix):
        return short_path[len(package_prefix):]
    package_index = short_path.find("/" + package_prefix)
    if package_index != -1:
        return short_path[package_index + len(package_prefix) + 1:]
    fail("Cannot map %s relative to package %s" % (short_path, package))

def _webview_plugin_zip_impl(ctx):
    args = ctx.actions.args()
    inputs = []
    root_dir = ctx.attr.root_dir.strip("/")
    if not root_dir:
        fail("root_dir must not be empty")

    for target, entry_name in ctx.attr.files.items():
        file = _single_file(target, "files")
        inputs.append(file)
        args.add("%s=%s" % (_entry(root_dir, entry_name), file.path))

    for file in ctx.files.native_files:
        inputs.append(file)
        args.add("%s=%s" % (_entry(root_dir, _relative_to_package(file, ctx.label.package)), file.path))

    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")

    ctx.actions.run(
        inputs = inputs,
        outputs = [ctx.outputs.out],
        executable = ctx.executable._zipper,
        arguments = [
            "c",
            ctx.outputs.out.path,
            args,
        ],
        mnemonic = "WebViewPluginZip",
        progress_message = "Creating WebView plugin zip %s" % ctx.outputs.out.short_path,
    )

    return [DefaultInfo(files = depset([ctx.outputs.out]))]

webview_plugin_zip = rule(
    implementation = _webview_plugin_zip_impl,
    attrs = {
        "files": attr.label_keyed_string_dict(
            allow_files = True,
            mandatory = True,
        ),
        "native_files": attr.label_list(
            allow_files = True,
        ),
        "out": attr.output(mandatory = True),
        "root_dir": attr.string(mandatory = True),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
            cfg = "exec",
        ),
    },
)
