parameter_lists_copy = [m for m in parameter_lists]
for <caret>m in parameter_lists_copy:
    if param_index >= len(m.GetParameters()):
        parameter_lists.remove(m)
