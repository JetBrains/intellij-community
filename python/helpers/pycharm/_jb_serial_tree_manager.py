# coding=utf-8


class SerialTreeManager(object):
    """
    Manages output tree by building it from flat test names.
    """

    def __init__(self):
        super(SerialTreeManager, self).__init__()
        # Currently active branch as list. New nodes go to this branch
        self.current_branch = []
        # node unique name to its nodeId
        self._node_ids_dict = {}
        # Node id mast be incremented for each new branch
        self._max_node_id = 0

    def _calculate_relation(self, branch_as_list):
        """
        Get relation of branch_as_list to current branch.
        :return: tuple. First argument could be: "same", "child", "parent" or "sibling"(need to start new tree)
        Second argument is relative path from current branch to child if argument is child
        """
        if branch_as_list == self.current_branch:
            return "same", None

        hierarchy_name_len = len(branch_as_list)
        current_branch_len = len(self.current_branch)

        if hierarchy_name_len > current_branch_len and branch_as_list[0:current_branch_len] == self.current_branch:
            return "child", branch_as_list[current_branch_len:]

        if hierarchy_name_len < current_branch_len and self.current_branch[0:hierarchy_name_len] == branch_as_list:
            return "parent", None

        return "sibling", None

    def _add_new_node(self, new_node_name):
        """
        Adds new node to branch
        """
        self.current_branch.append(new_node_name)
        self._max_node_id += 1
        self._node_ids_dict[".".join(self.current_branch)] = self._max_node_id

    def level_opened(self, test_as_list, func_to_open):
        """
        To be called on test start.

        :param test_as_list: test name splitted as list
        :param func_to_open: func to be called if test can open new level
        :return: None if new level opened, or tuple of command client should execute and try opening level again
         Command is "open" (open provided level) or "close" (close it). Second item is test name as list
        """
        relation, relative_path = self._calculate_relation(test_as_list)
        if relation == 'same':
            return  # Opening same level?
        if relation == 'child':
            # If one level -- open new level gracefully
            if len(relative_path) == 1:
                self._add_new_node(relative_path[0])
                func_to_open()
                return None
            else:
                # Open previous level
                return [("open", self.current_branch + relative_path[0:1])]
        if relation == "sibling":
            if self.current_branch:
                # Different tree, close whole branch
                return [("close", self.current_branch)]
            else:
                return None
        if relation == 'parent':
            # Opening parent? Insane
            pass

    def level_closed(self, test_as_list, func_to_close):
        """
        To be called on test end or failure.

        See level_opened doc.
        """
        relation, relative_path = self._calculate_relation(test_as_list)
        if relation == 'same':
            # Closing current level
            func_to_close()
            self.current_branch.pop()
        if relation == 'child':
            return None

        if relation == 'sibling':
            pass
        if relation == 'parent':
            return [("close", self.current_branch)]

    @property
    def parent_branch(self):
        return self.current_branch[:-1] if self.current_branch else None

    def _get_node_id(self, branch):
        return self._node_ids_dict[".".join(branch)]

    # Part of contract
    # noinspection PyUnusedLocal
    def get_node_ids(self, test_name):
        """

        :return: (current_node_id, parent_node_id)
        """
        current = self._get_node_id(self.current_branch)
        parent = self._get_node_id(self.parent_branch) if self.parent_branch else "0"
        return str(current), str(parent)
