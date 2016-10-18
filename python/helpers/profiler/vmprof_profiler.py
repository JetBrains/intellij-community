import os
import shutil
import tempfile
import vmprof

import six
from _prof_imports import TreeStats, CallTreeStat


class VmProfProfile(object):
    """ Wrapper class that represents VmProf Python profiling backend with API matching
        the cProfile.
    """

    def __init__(self):
        self.stats = None
        self.basepath = None
        self.file = None
        self.is_enabled = False

    def runcall(self, func, *args, **kw):
        self.enable()
        try:
            return func(*args, **kw)
        finally:
            self.disable()

    def enable(self):
        if not self.is_enabled:
            if not os.path.exists(self.basepath):
                os.makedirs(self.basepath)
            self.file = tempfile.NamedTemporaryFile(delete=False, dir=self.basepath)
            try:
                vmprof.enable(self.file.fileno(), lines=True)
            except:
                vmprof.enable(self.file.fileno())
            self.is_enabled = True

    def disable(self):
        if self.is_enabled:
            vmprof.disable()
            self.file.close()
            self.is_enabled = False

    def create_stats(self):
        return None

    def getstats(self):
        self.create_stats()

        return self.stats

    def dump_stats(self, file):
        shutil.copyfile(self.file.name, file)

    def _walk_tree(self, parent, node, callback):
        tree = callback(parent, node)
        for c in six.itervalues(node.children):
            self._walk_tree(node, c, callback)
        return tree

    def tree_stats_to_response(self, filename, response):
        tree_stats_to_response(filename, response)

    def snapshot_extension(self):
        return '.prof'


def _walk_tree(parent, node, callback):
    if node is None:
        return None
    tree = callback(parent, node)
    for c in six.itervalues(node.children):
        _walk_tree(tree, c, callback)
    return tree


def tree_stats_to_response(filename, response):
    stats = vmprof.read_profile(filename)

    response.tree_stats = TreeStats()
    response.tree_stats.sampling_interval = vmprof.DEFAULT_PERIOD

    try:
        tree = stats.get_tree()
    except vmprof.stats.EmptyProfileFile:
        tree = None

    def convert(parent, node):
        tstats = CallTreeStat()
        tstats.name = node.name
        tstats.count = node.count
        tstats.children = []
        tstats.line_count = getattr(node, 'lines', {})

        if parent is not None:
            if parent.children is None:
                parent.children = []
            parent.children.append(tstats)

        return tstats

    response.tree_stats.call_tree = _walk_tree(None, tree, convert)
