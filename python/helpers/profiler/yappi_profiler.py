import yappi

class YappiProfile(object):
    """ Wrapper class that represents Yappi profiling backend with API matching
        the cProfile.
    """
    def __init__(self):
        self.stats = None
        yappi.set_clock_type("wall")

    def runcall(self, func, *args, **kw):
        self.enable()
        try:
            return func(*args, **kw)
        finally:
            self.disable()

    def enable(self):
        yappi.start()

    def disable(self):
        yappi.stop()

    def convert_stats_to_dict(self, stats):
        result = []
        for stat in stats:
            func_dict = {
                'filename': stat.module,
                'name': stat.name,
                'line': stat.lineno,
                'calls': stat.ncall,
                'total_time': stat.ttot,
                'cumulative_time': stat.tsub,
                'thread_id': stat.ctx_id
            }
            result.append(func_dict)
        return result

    def create_stats(self):
        self.stats = yappi.get_func_stats()

    def getstats(self):
        self.create_stats()
        return self.stats

    def dump_stats(self, file):
        self.getstats().save(file, type="ystat")

