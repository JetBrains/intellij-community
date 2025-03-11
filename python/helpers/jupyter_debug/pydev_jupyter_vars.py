from _pydevd_bundle.pydevd_constants import dict_keys, NEXT_VALUE_SEPARATOR
from _pydevd_bundle.pydevd_tables import exec_table_command
from _pydevd_bundle.pydevd_user_type_renderers import parse_set_type_renderers_message
from _pydevd_bundle.pydevd_vars import resolve_compound_var_object_fields, \
    table_like_struct_to_xml, eval_in_context, resolve_var_object
from _pydevd_bundle.pydevd_xml import frame_vars_to_xml, should_evaluate_full_value, \
    var_to_xml


def evaluate(expression, do_trim):
    ipython_shell = get_ipython()
    user_type_renderers = getattr(ipython_shell, "user_type_renderers", None)
    namespace = ipython_shell.user_ns
    result = eval_in_context(expression, namespace, namespace)
    xml = "<xml>"
    xml += var_to_xml(result, expression, do_trim=do_trim, user_type_renderers=user_type_renderers)
    xml += "</xml>"
    print(xml)


def get_frame(group_type):
    ipython_shell = get_ipython()
    user_ns = ipython_shell.user_ns
    hidden_ns = ipython_shell.user_ns_hidden
    user_type_renderers = getattr(ipython_shell, "user_type_renderers", None)
    xml = "<xml>"
    xml += frame_vars_to_xml(user_ns, group_type, hidden_ns, user_type_renderers)
    xml += "</xml>"
    print(xml)


def get_variable(pydev_text):
    ipython_shell = get_ipython()
    user_type_renderers = getattr(ipython_shell, "user_type_renderers", None)
    val_dict = resolve_compound_var_object_fields(ipython_shell.user_ns, pydev_text, user_type_renderers)
    if val_dict is None:
        val_dict = {}

    xml_list = ["<xml>"]
    for k in dict_keys(val_dict):
        val = val_dict[k]
        evaluate_full_value = should_evaluate_full_value(val)
        xml_list.append(var_to_xml(val, k, evaluate_full_value=evaluate_full_value, user_type_renderers=user_type_renderers))
    xml_list.append("</xml>")
    print(''.join(xml_list))


def get_array(text):
    ipython_shell = get_ipython()
    namespace = ipython_shell.user_ns
    roffset, coffset, rows, cols, format, attrs = text.split('\t', 5)
    name = attrs.split("\t")[-1]
    var = eval_in_context(name, namespace, namespace)
    xml = table_like_struct_to_xml(var, name, int(roffset), int(coffset), int(rows), int(cols), format)
    print(xml)


def load_full_value(scope_attrs):
    ipython_shell = get_ipython()
    user_type_renderers = getattr(ipython_shell, "user_type_renderers", None)
    namespace = ipython_shell.user_ns
    vars = scope_attrs.split(NEXT_VALUE_SEPARATOR)
    xml_list =  ["<xml>"]
    for var_attrs in vars:
        var_attrs = var_attrs.strip()
        if len(var_attrs) == 0:
            continue
        if '\t' in var_attrs:
            name, attrs = var_attrs.split('\t', 1)
        else:
            name = var_attrs
            attrs = None
        if name in namespace.keys():
            var_object = resolve_var_object(namespace[name], attrs)
        else:
            var_object = eval_in_context(name, namespace, namespace)

        xml_list.append(var_to_xml(var_object, name, evaluate_full_value=True, user_type_renderers=user_type_renderers))
    xml_list.append("</xml>")
    print(''.join(xml_list))


def table_command(command_text):
    ipython_shell = get_ipython()
    namespace = ipython_shell.user_ns
    command, command_type, start_index, end_index, format = command_text.split(NEXT_VALUE_SEPARATOR)

    try:
        start_index = int(start_index)
        end_index = int(end_index)
    except ValueError:
        start_index = None
        end_index = None

    status, res = exec_table_command(command, command_type, start_index, end_index, format, namespace, namespace)
    print(res)

def serializeImage(img):
    if len(img.shape) != 2:
        return None

    if img.shape[0] > 1024 or img.shape[1] > 1024:
        return None

    result = "{\n"
    result += "  \"height\": {},\n  \"width\": {},\n  \"value\": [\n".format(img.shape[0], img.shape[1])

    for y in range(img.shape[0]):
        result += "    ["
        for x in range(img.shape[1]):
            result += "{}".format(img[y][x])
            if x < img.shape[1] - 1:
                result += ", "
        result += "]"
        if y < img.shape[0] - 1:
            result += ", \n"

    result += "\n  ]\n}"

    return result

def image_command(command_text):
    ipython_shell = get_ipython()
    namespace = ipython_shell.user_ns
    try:
      var_value = eval_in_context(command_text, namespace, namespace)
      json_result = serializeImage(var_value)
    except Exception as e:
        print(e)
    print(command_text)
    print(json_result)


def set_user_type_renderers(message):
    ipython_shell = get_ipython()
    try:
        renderers = parse_set_type_renderers_message(message)
        ipython_shell.user_type_renderers = renderers
    except:
        pass
