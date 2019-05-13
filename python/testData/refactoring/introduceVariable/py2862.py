response = self._reqXml('PUT', <selection>'/import/' + urllib.quote(projectId) + '/issues?' +
                                       urllib.urlencode({'assigneeGroup': assigneeGroup})</selection>,
                                xml, 400).toxml().encode('utf-8')