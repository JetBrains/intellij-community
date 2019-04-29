# coding=utf-8


class ParallelTreeManager(object):
    """
    Manages output tree by building it from flat test names.
    """

    def __init__(self):
        super(ParallelTreeManager, self).__init__()
        self._max_node_id = 0
        self._branches = dict()  # key is test name as tuple, value is tuple of test_id, parent_id

    def _next_node_id(self):
        self._max_node_id += 1
        return self._max_node_id

    def level_opened(self, test_as_list, func_to_open):
        """
        To be called on test start.

        :param test_as_list: test name splitted as list
        :param func_to_open: func to be called if test can open new level
        :return: None if new level opened, or tuple of command client should execute and try opening level again
         Command is "open" (open provided level) or "close" (close it). Second item is test name as list
        """

        if tuple(test_as_list) in self._branches:
            # We have parent, ok
            func_to_open()
            return None
        elif len(test_as_list) == 1:
            self._branches[tuple(test_as_list)] = (self._next_node_id(), 0)
            func_to_open()
            return None

        commands = []

        parent_id = 0
        for i in range(len(test_as_list)):
            tmp_parent_as_list = test_as_list[0:i + 1]
            try:
                parent_id, _ = self._branches[tuple(tmp_parent_as_list)]
            except KeyError:
                node_id = self._next_node_id()
                self._branches[tuple(tmp_parent_as_list)] = (node_id, parent_id)
                parent_id = node_id
                if tmp_parent_as_list != test_as_list: # Different test opened
                    commands.append(("open", tmp_parent_as_list))
        if commands:
            return commands
        else:
            func_to_open()

    def level_closed(self, test_as_list, func_to_close):
        """
        To be called on test end or failure.

        See level_opened doc.
        """
        func_to_close()

    # Part of contract
    def get_node_ids(self, test_name):
        """

        :return: (current_node_id, parent_node_id) or None, None if message must be ignored
        """
        try:
            return self._branches[tuple(test_name.split("."))]
        except KeyError:
            return None, None
