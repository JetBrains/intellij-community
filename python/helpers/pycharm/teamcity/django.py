from __future__ import absolute_import
from django.test.runner import DiscoverRunner
from teamcity.unittestpy import TeamcityTestRunner


class TeamcityDjangoRunner(DiscoverRunner):
    def run_suite(self, suite, **kwargs):
        return TeamcityTestRunner().run(suite)
