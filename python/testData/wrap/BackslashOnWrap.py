def copy_location(new_node, old_node):
    for attr in 'lineno', 'col_offset':
        if attr in old_node._attributes and attr in new_node._attributes <caret>