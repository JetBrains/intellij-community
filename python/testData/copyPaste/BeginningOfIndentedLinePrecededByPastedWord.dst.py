def func():
    for des_name, comp in keys_to_delete:
        cc_list = cell_classes_by_comp_dict[comp]
        if len(cc_list) == 1:
            del cell_classes_by_comp_dict[comp]
        else:
            new_cc_list = [cc for cc in cc_list if cc.design_name != des_name]
            cell_classes_by_comp_dict[comp] = copy.deepcopy(new_cc_list)
    <caret>cell_classes_by_comp_dict = \
        deepcopy(cell_classes_by_comp_dict)