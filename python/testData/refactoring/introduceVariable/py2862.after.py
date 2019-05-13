a = '/import/' + urllib.quote(projectId) + '/issues?' + urllib.urlencode({'assigneeGroup': assigneeGroup})
response = self._reqXml('PUT', a,
                        xml, 400).toxml().encode('utf-8')