parameter_lists_copy = [bar for bar in parameter_lists]
for bar in parameter_lists_copy:
    if param_index >= len(bar.GetParameters()):
        parameter_lists.remove(bar)
