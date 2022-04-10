/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

{
  "note": "May https://vega.github.io/vega/docs/ be with you",
  "$schema": "https://vega.github.io/schema/vega/v4.3.0.json",
  "description": "Whole project highlighting - percentiles",
  "title": "Whole project highlighting - percentiles",
  "width": 800,
  "height": 500,
  "padding": 5,
  "autosize": {"type": "pad", "resize": true},
  "signals": [
    {
      "name": "clear",
      "value": true,
      "on": [
        {"events": "mouseup[!event.item]", "update": "true", "force": true}
      ]
    },
    {
      "name": "shift",
      "value": false,
      "on": [
        {
          "events": "@legendSymbol:click, @legendLabel:click",
          "update": "event.shiftKey",
          "force": true
        }
      ]
    },
    {
      "name": "clicked",
      "value": null,
      "on": [
        {
          "events": "@legendSymbol:click, @legendLabel:click",
          "comment": "note: here `datum` is `selected` data set",
          "update": "{value: datum.value}",
          "force": true
        }
      ]
    },
    {
      "name": "branchShift",
      "value": false,
      "on": [
        {
          "events": "@branchLegendSymbol:click, @branchLegendLabel:click",
          "update": "event.shiftKey",
          "force": true
        }
      ]
    },
    {
      "name": "branchClicked",
      "value": null,
      "on": [
        {
          "events": "@branchLegendSymbol:click, @branchLegendLabel:click",
          "comment": "note: here `datum` is `selected` data set",
          "update": "{value: datum.value}",
          "force": true
        }
      ]
    },
    {
      "name": "brush",
      "value": 0,
      "on": [
        {"events": {"signal": "clear"}, "update": "clear ? [0, 0] : brush"},
        {"events": "@xaxis:mousedown", "update": "[x(), x()]"},
        {
          "events": "[@xaxis:mousedown, window:mouseup] > window:mousemove!",
          "update": "[brush[0], clamp(x(), 0, width)]"
        },
        {
          "events": {"signal": "delta"},
          "update": "clampRange([anchor[0] + delta, anchor[1] + delta], 0, width)"
        }
      ]
    },
    {
      "name": "anchor",
      "value": null,
      "on": [{"events": "@brush:mousedown", "update": "slice(brush)"}]
    },
    {
      "name": "xdown",
      "value": 0,
      "on": [{"events": "@brush:mousedown", "update": "x()"}]
    },
    {
      "name": "delta",
      "value": 0,
      "on": [
        {
          "events": "[@brush:mousedown, window:mouseup] > window:mousemove!",
          "update": "x() - xdown"
        }
      ]
    },
    {
      "name": "domain",
      "on": [
        {
          "events": {"signal": "brush"},
          "update": "span(brush) ? invert('x', brush) : null"
        }
      ]
    }
  ],
  "data": [
    {
      "name": "table",
      "_values": {
        "aggregations" : {
          "benchmarks" : {
            "doc_count_error_upper_bound" : 0,
            "sum_other_doc_count" : 0,
            "buckets" : [
              {
                "key" : "allKtFilesIn-intellij",
                "doc_count" : 5406,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 3604,
                  "buckets" : [
                    {
                      "key" : "kt-212-1.6.0",
                      "doc_count" : 1802,
                      "buildId" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : 145918610,
                            "doc_count" : 901,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-27T19:31:00.000Z",
                                  "key" : 1635363060000,
                                  "doc_count" : 901,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 155.26190476190476,
                                      "50.0" : 315.54545454545456,
                                      "80.0" : 2253.7522222222233,
                                      "95.0" : 4687.699999999997,
                                      "98.0" : 9755.76
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          },
                          {
                            "key" : 146085424,
                            "doc_count" : 901,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-27T21:34:07.000Z",
                                  "key" : 1635370447000,
                                  "doc_count" : 901,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 153.83333333333331,
                                      "50.0" : 313.03663003663,
                                      "80.0" : 2240.076666666667,
                                      "95.0" : 4766.849999999997,
                                      "98.0" : 10039.04
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-emptyProfile-intellij",
                "doc_count" : 3604,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 1802,
                  "buckets" : [
                    {
                      "key" : "kt-212-master",
                      "doc_count" : 1802,
                      "buildId" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : 145848616,
                            "doc_count" : 901,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-27T02:26:35.000Z",
                                  "key" : 1635301595000,
                                  "doc_count" : 901,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 47.55357142857143,
                                      "50.0" : 140.25,
                                      "80.0" : 999.4466666666672,
                                      "95.0" : 1882.35,
                                      "98.0" : 3973.2300000000014
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          },
                          {
                            "key" : 146085479,
                            "doc_count" : 901,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-28T01:46:14.000Z",
                                  "key" : 1635385574000,
                                  "doc_count" : 901,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 44.45,
                                      "50.0" : 135.125,
                                      "80.0" : 991.45,
                                      "95.0" : 1879.749999999999,
                                      "98.0" : 3898.0600000000018
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-emptyProfile-kotlin",
                "doc_count" : 1802,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : "master",
                      "doc_count" : 1802,
                      "buildId" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : 145848612,
                            "doc_count" : 901,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-27T03:15:31.000Z",
                                  "key" : 1635304531000,
                                  "doc_count" : 901,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 375.2142857142857,
                                      "50.0" : 1488.7576923076922,
                                      "80.0" : 3451.1375000000007,
                                      "95.0" : 6886.699999999999,
                                      "98.0" : 9623.390000000009
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          },
                          {
                            "key" : 146085617,
                            "doc_count" : 901,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-28T02:39:28.000Z",
                                  "key" : 1635388768000,
                                  "doc_count" : 901,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 381.5,
                                      "50.0" : 1479.1200000000001,
                                      "80.0" : 3434.1200000000003,
                                      "95.0" : 6909.449999999996,
                                      "98.0" : 10076.540000000008
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-emptyProfile-space",
                "doc_count" : 925,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : "master",
                      "doc_count" : 925,
                      "buildId" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : 145848624,
                            "doc_count" : 841,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-27T05:36:21.000Z",
                                  "key" : 1635312981000,
                                  "doc_count" : 841,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 177.9375,
                                      "50.0" : 1153.3311688311687,
                                      "80.0" : 4256.8200000000015,
                                      "95.0" : 9398.999999999978,
                                      "98.0" : 17311.690000000068
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          },
                          {
                            "key" : 146085655,
                            "doc_count" : 84,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-28T06:44:46.000Z",
                                  "key" : 1635403486000,
                                  "doc_count" : 84,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 3977.0,
                                      "50.0" : 9301.0,
                                      "80.0" : 16860.5,
                                      "95.0" : 25928.99999999998,
                                      "98.0" : 47614.259999999995
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-kotlin",
                "doc_count" : 382,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : "master",
                      "doc_count" : 382,
                      "buildId" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : 145848634,
                            "doc_count" : 382,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-27T08:38:07.000Z",
                                  "key" : 1635323887000,
                                  "doc_count" : 382,
                                  "percentiles" : {
                                    "values" : {
                                      "25.0" : 3160.0,
                                      "50.0" : 5288.6,
                                      "80.0" : 8606.000000000002,
                                      "95.0" : 16671.199999999986,
                                      "98.0" : 113430.8000000001
                                    }
                                  }
                                }
                              ],
                              "interval" : "1s"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            ]
          }
        }
      },
      "url": {
        // "comment": "source index pattern",
        "index": "kotlin_ide_benchmarks*",
        // "comment": "it's a body of ES _search query to check query place it into `POST /kotlin_ide_benchmarks*/_search`",
        // "comment": "it uses Kibana specific %timefilter% for time frame selection",
        "body": {
            "size": 0,
            "query": {
              "bool": {
                "must": [
                  {"range": {"buildTimestamp": {"%timefilter%": true}}}
                ],
                "filter": [
                  {
                    "prefix": {
                      "benchmark.keyword": {
                        "value": "allKtFilesIn"
                      }
                    }
                  }
                ]
              }
            },
            "aggs": {
              "benchmarks": {
                "terms": {
                  "field": "benchmark.keyword",
                  "size": 500
                },
                "aggs": {
                  "branch": {
                    "terms": {
                      "size": 10,
                      "field": "buildBranch.keyword"
                    },

                    "aggs": {
                      "buildId": {
                        "terms": {
                          "field": "buildId"
                        },
                        "aggs": {
                          "values": {
                            "auto_date_histogram": {
                                "buckets": 1,
                                "field": "buildTimestamp"
                            },
                            "aggs": {
                              "percentiles": {
                                "percentiles": {
                                  "field": "metricValue",
                                  "percents": [
                                    25,
                                    50,
                                    80,
                                    95,
                                    98
                                  ]
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        },
      "format": {"property": "aggregations"},
      "transform": [
        {"type": "project", "fields": ["benchmarks"]},
        {"type": "flatten", "fields": ["benchmarks.buckets"], "as": ["benchmark_buckets"]},
        {"type": "project", "fields": ["benchmark_buckets.key", "benchmark_buckets.branch"], "as": ["benchmark", "benchmark_buckets_branch"]},
        {"type": "flatten", "fields": ["benchmark_buckets_branch.buckets"], "as": ["branch_buckets"]},
        {"type": "project", "fields": ["benchmark", "branch_buckets.key", "branch_buckets.buildId.buckets"], "as": ["benchmark", "branch", "branch_buildId_buckets"]},
        {"type": "flatten", "fields": ["branch_buildId_buckets"], "as": ["buildId_branches"]},
        {"type": "project", "fields": ["benchmark", "branch", "buildId_branches.key", "buildId_branches.values.buckets"], "as": ["benchmark", "branch", "buildId", "buildId_branches_buckets"]},
        {"type": "flatten", "fields": ["buildId_branches_buckets"], "as": ["buildId_branches_buckets_values"]},
        {"type": "project", "fields": ["benchmark", "branch", "buildId",  "buildId_branches_buckets_values.key_as_string", "buildId_branches_buckets_values.percentiles.values"], "as": ["benchmark", "branch", "buildId", "buildTimestamp", "percentiles"]},
        {"type": "project", "fields": ["benchmark", "branch", "buildId", "buildTimestamp", "percentiles[25.0]", "percentiles[50.0]", "percentiles[80.0]", "percentiles[95.0]", "percentiles[98.0]"], "as":["benchmark", "branch", "buildId", "buildTimestamp", "p25", "p50", "p80", "p95", "p98"]},
        {
        "type": "fold",
        "fields": ["p25", "p50", "p80", "p95", "p98"]
        },
        {"type": "project", "fields": ["benchmark", "branch",  "buildId", "buildTimestamp", "key", "value"], "as":["benchmark", "branch", "buildId", "buildTimestamp", "percentile", "value"]},
        {
          "type": "formula",
          "as": "timestamp",
          "expr": "timeFormat(toDate(datum.buildTimestamp), '%Y-%m-%d %H:%M')"
        },
        {
          "type": "formula",
          "as": "projectName",
          "expr": "replace(datum.benchmark, /allKtFilesIn(-emptyProfile)?-?(\\w+)/, '$2$1') + ' [' + datum.branch + '] '"
        },
        {
          "type": "formula",
          "as": "benchmark",
          "expr": "replace(datum.benchmark, /allKtFilesIn(-emptyProfile)?-?(\\w+)/, '$2') + ' [' + datum.branch + '] ' + datum.timestamp"
        },
        {"type": "collect","sort": {"field": "benchmark"}}
      ]
    },
    {
      "name": "selected",      "on": [
        {"trigger": "clear", "remove": true},
        {"trigger": "!shift", "remove": true},
        {"trigger": "!shift && clicked", "insert": "clicked"},
        {"trigger": "shift && clicked", "toggle": "clicked"}
      ]
    },
    {
      "name": "selectedProject",      "on": [
        {"trigger": "clear", "remove": true},
        {"trigger": "!shift", "remove": true},
        {"trigger": "!shift && clicked", "insert": "clicked"},
        {"trigger": "shift && clicked", "toggle": "clicked"}
      ]
    },
    {
        "name": "selectedBranch",
        "on": [
         {"trigger": "clear", "remove": true},
         {"trigger": "!branchShift", "remove": true},
         {"trigger": "!branchShift && branchClicked", "insert": "branchClicked"},
         {"trigger": "branchShift && branchClicked", "toggle": "branchClicked"}
        ]
    }
  ],

  "scales": [
    {
      "name": "xscale",
      "type": "band",
      "domain": {"data": "table", "field": "percentile"},
      "range": "width",
      "padding": 0.2
    },
    {
      "name": "yscale",
      "type": "linear",
      "domain": {"data": "table", "field": "value"},
      "range": "height",
      "round": true,
      "zero": true,
      "nice": true
    },
    {
      "name": "caseColor",
      "type": "ordinal",
      "domain": {"data": "table", "field": "benchmark"},
      "range": {"scheme": "category10"}
    },
    {
      "name": "projectColor",
      "type": "ordinal",
      "domain": {"data": "table", "field": "projectName"},
      "range": {"scheme": "category10"}
    },
    {
      "name": "branchColor",
      "type": "ordinal",
      "domain": {"data": "table", "field": "branch"},
      "range": {"scheme": "dark2"}
    }
  ],

  "axes": [
    {"orient": "left", "grid": true, "scale": "yscale", "tickSize": 0, "labelPadding": 4, "zindex": 1},
    {"orient": "bottom", "grid": true, "scale": "xscale"}
  ],
  "legends": [
    {
      "title": "Project",
      "stroke": "projectColor",
      "strokeColor": "#ccc",
      "padding": 8,
      "cornerRadius": 4,
      "symbolLimit": 50,
      "labelLimit": 300,
      "encode": {
        "symbols": {
          "name": "legendSymbol",
          "interactive": true,
          "update": {
            "fill": {"value": "transparent"},
            "strokeWidth": {"value": 2},
            "opacity": [
              {
                "comment": "here `datum` is `selectedProject` data set",
                "test": "!length(data('selectedProject')) || indata('selectedProject', 'value', datum.value)",
                "value": 0.7
              },
              {"value": 0.15}
            ],
            "size": {"value": 64}
          }
        },
        "labels": {
          "name": "legendLabel",
          "interactive": true,
          "update": {
            "opacity": [
              {
                "comment": "here `datum` is `selectedProject` data set",
                "test": "!length(data('selectedProject')) || indata('selectedProject', 'value', datum.value)",
                "value": 1
              },
              {"value": 0.25}
            ]
          }
        }
      }
    },
    {
      "title": "Cases",
      "stroke": "caseColor",
      "strokeColor": "#ccc",
      "padding": 8,
      "cornerRadius": 4,
      "symbolLimit": 50,
      "labelLimit": 300,
      "encode": {
        "symbols": {
          "name": "legendSymbol",
          "interactive": true,
          "update": {
            "fill": {"value": "transparent"},
            "strokeWidth": {"value": 2},
            "opacity": [
              {
                "comment": "here `datum` is `selected` data set",
                "test": "!length(data('selected')) || indata('selected', 'value', datum.value)",
                "value": 0.7
              },
              {"value": 0.15}
            ],
            "size": {"value": 64}
          }
        },
        "labels": {
          "name": "legendLabel",
          "interactive": true,
          "update": {
            "opacity": [
              {
                "comment": "here `datum` is `selected` data set",
                "test": "!length(data('selected')) || indata('selected', 'value', datum.value)",
                "value": 1
              },
              {"value": 0.25}
            ]
          }
        }
      }
    }
  ],

  "marks": [
    {
      "type": "group",

      "from": {
        "facet": {
          "data": "table",
          "name": "facet",
          "groupby": "percentile"
        }
      },

      "encode": {
        "enter": {
          "x": {"scale": "xscale", "field": "percentile"}
        }
      },

      "signals": [
        {"name": "width", "update": "bandwidth('xscale')"}
      ],

      "scales": [
        {
          "name": "pos",
          "type": "band",
          "range": "width",
          "domain": {"data": "facet", "field": "benchmark"}
        }
      ],

      "marks": [
        {
          "name": "bars",
          "from": {"data": "facet"},
          "type": "rect",
          "encode": {
            "enter": {
              "x": {"scale": "pos", "field": "benchmark"},
              "width": {"scale": "pos", "band": 1},
              "y": {"scale": "yscale", "field": "value"},
              "y2": {"scale": "yscale", "value": 0},
              "fill": {"scale": "caseColor", "field": "benchmark"},
              "opacity": [
                {
                  "test": "(!length(data('selectedProject')) || indata('selectedProject', 'value', datum.projectName))",
                  "value": 0.95
                },
                {"value": 0.15}
              ]
            },
            "update": {
  "opacity": [
                {
                  "test": "(!length(data('selectedProject')) || indata('selectedProject', 'value', datum.projectName))",
                  "value": 0.95
                },
                {"value": 0.15}
              ]
            }
          }
        }
      ]
    }
  ]
}

