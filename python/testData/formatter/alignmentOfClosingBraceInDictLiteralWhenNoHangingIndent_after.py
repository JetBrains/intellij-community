class Checkpoints(webapp2.RequestHandler):
    def get(self):
        self.response.write(json.dumps({"meta": {"code": 400,
                                                 "errorType": "paramError",
                                                 "errorDetail": "Parameter 'api_key' is missing"
                                                 },
                                        "response": {}
                                        }))
