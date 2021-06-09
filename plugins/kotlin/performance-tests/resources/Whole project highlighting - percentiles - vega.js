/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

{
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
                "key" : "allKtFilesIn-kotlin",
                "doc_count" : 1805,
                "buildId" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : 122583347,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T00:00:00.000Z",
                            "key" : 1622678400000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 215.33333333333331,
                                "50.0" : 839.0,
                                "80.0" : 1966.6000000000001,
                                "95.0" : 3934.9499999999935,
                                "99.0" : 6307.070000000003
                              }
                            }
                          }
                        ],
                        "interval" : "1h"
                      }
                    },
                    {
                      "key" : 122724969,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T20:03:38.000Z",
                            "key" : 1622750618000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 223.7222222222222,
                                "50.0" : 841.0416666666666,
                                "80.0" : 1968.5333333333335,
                                "95.0" : 3817.099999999999,
                                "99.0" : 6111.0
                              }
                            }
                          }
                        ],
                        "interval" : "1s"
                      }
                    },
                    {
                      "key" : 122889625,
                      "doc_count" : 3,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-04T20:03:59.000Z",
                            "key" : 1622837039000,
                            "doc_count" : 3,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 4498.0,
                                "50.0" : 4651.0,
                                "80.0" : 11340.700000000003,
                                "95.0" : 12084.0,
                                "99.0" : 12084.0
                              }
                            }
                          }
                        ],
                        "interval" : "1s"
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-emptyProfile-space",
                "doc_count" : 1802,
                "buildId" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : 122583349,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T02:00:00.000Z",
                            "key" : 1622685600000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 104.68939393939395,
                                "50.0" : 431.66666666666663,
                                "80.0" : 2149.0000000000005,
                                "95.0" : 4442.599999999995,
                                "99.0" : 8317.480000000001
                              }
                            }
                          }
                        ],
                        "interval" : "1h"
                      }
                    },
                    {
                      "key" : 122726181,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-04T00:30:48.000Z",
                            "key" : 1622766648000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 103.10833333333332,
                                "50.0" : 398.0,
                                "80.0" : 2108.8,
                                "95.0" : 4356.399999999995,
                                "99.0" : 8103.200000000001
                              }
                            }
                          }
                        ],
                        "interval" : "1s"
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-space",
                "doc_count" : 1802,
                "buildId" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : 122583370,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T03:00:00.000Z",
                            "key" : 1622689200000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 105.04285714285713,
                                "50.0" : 436.71999999999997,
                                "80.0" : 2201.5000000000005,
                                "95.0" : 4637.699999999993,
                                "99.0" : 8536.66
                              }
                            }
                          }
                        ],
                        "interval" : "1h"
                      }
                    },
                    {
                      "key" : 122726204,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-04T03:03:10.000Z",
                            "key" : 1622775790000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 101.33333333333333,
                                "50.0" : 384.84,
                                "80.0" : 2076.0000000000005,
                                "95.0" : 4324.149999999992,
                                "99.0" : 8067.92
                              }
                            }
                          }
                        ],
                        "interval" : "1s"
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-intellij",
                "doc_count" : 1306,
                "buildId" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : 122726093,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T21:29:56.000Z",
                            "key" : 1622755796000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 215.3125,
                                "50.0" : 405.0,
                                "80.0" : 2261.3000000000006,
                                "95.0" : 4750.149999999997,
                                "99.0" : 10093.630000000001
                              }
                            }
                          }
                        ],
                        "interval" : "1s"
                      }
                    },
                    {
                      "key" : 122582874,
                      "doc_count" : 405,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T00:00:00.000Z",
                            "key" : 1622678400000,
                            "doc_count" : 405,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 122.5,
                                "50.0" : 202.0,
                                "80.0" : 377.0,
                                "95.0" : 575.5,
                                "99.0" : 1630.999999999998
                              }
                            }
                          }
                        ],
                        "interval" : "1m"
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-emptyProfile-intellij",
                "doc_count" : 901,
                "buildId" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : 122726183,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-04T02:09:12.000Z",
                            "key" : 1622772552000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 102.37962962962963,
                                "50.0" : 225.92222222222222,
                                "80.0" : 1097.3000000000002,
                                "95.0" : 1932.8999999999999,
                                "99.0" : 4584.62000000001
                              }
                            }
                          }
                        ],
                        "interval" : "1s"
                      }
                    }
                  ]
                }
              },
              {
                "key" : "allKtFilesIn-emptyProfile-kotlin",
                "doc_count" : 901,
                "buildId" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : 122726179,
                      "doc_count" : 901,
                      "values" : {
                        "buckets" : [
                          {
                            "key_as_string" : "2021-06-03T23:03:06.000Z",
                            "key" : 1622761386000,
                            "doc_count" : 901,
                            "percentiles" : {
                              "values" : {
                                "25.0" : 223.95,
                                "50.0" : 842.0619047619048,
                                "80.0" : 1963.5666666666668,
                                "95.0" : 3977.2499999999973,
                                "99.0" : 6314.730000000001
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
      },
      "url": {
        //"comment": "source index pattern",
        "index": "kotlin_ide_benchmarks*",
        //"comment": "it's a body of ES _search query to check query place it into `POST /kotlin_ide_benchmarks*/_search`",
        //"comment": "it uses Kibana specific %timefilter% for time frame selection",
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
                                99
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
        },
      "format": {"property": "aggregations"},
      "transform": [
        {"type": "project", "fields": ["benchmarks"]},
        {"type": "flatten", "fields": ["benchmarks.buckets"], "as": ["benchmark_buckets"]},
        {"type": "project", "fields": ["benchmark_buckets.key", "benchmark_buckets.buildId"], "as": ["benchmark", "benchmark_buckets_buildId"]},
        {"type": "flatten", "fields": ["benchmark_buckets_buildId.buckets"], "as": ["buildId_buckets"]},
        {"type": "project", "fields": ["benchmark", "buildId_buckets.key", "buildId_buckets.values.buckets"], "as": ["benchmark", "buildId", "buildId_buckets_buckets"]},
        {"type": "flatten", "fields": ["buildId_buckets_buckets"], "as": ["buildId_buckets_buckets_values"]},
        {"type": "project", "fields": ["benchmark", "buildId", "buildId_buckets_buckets_values.key_as_string", "buildId_buckets_buckets_values.percentiles.values"], "as": ["benchmark", "buildId", "buildTimestamp", "percentiles"]},
        {"type": "project", "fields": ["benchmark", "buildId", "buildTimestamp", "percentiles[25.0]", "percentiles[50.0]", "percentiles[80.0]", "percentiles[95.0]", "percentiles[99.0]"], "as":["benchmark", "buildId", "buildTimestamp", "p25", "p50", "p80", "p95", "p99"]},
        {
        "type": "fold",
        "fields": ["p25", "p50", "p80", "p95", "p99"]
        },
        {"type": "project", "fields": ["benchmark", "buildId", "buildTimestamp", "key", "value"], "as":["benchmark", "buildId", "buildTimestamp", "percentile", "value"]},
        {
          "type": "formula",
          "as": "timestamp",
          "expr": "timeFormat(toDate(datum.buildTimestamp), '%Y-%m-%d %H:%M')"
        },
        {
          "type": "formula",
          "as": "benchmark",
          "expr": "replace(datum.benchmark, /allKtFilesIn(-emptyProfile)?-?(\\w+)/, '$2$1') + ' ' + datum.timestamp"
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
      "name": "color",
      "type": "ordinal",
      "domain": {"data": "table", "field": "benchmark"},
      "range": {"scheme": "category10"}
    },
    {
      "name": "branchColor",
      "type": "ordinal",
      "domain": {"data": "table", "field": "branch"},
      "range": "category"
    }
  ],

  "axes": [
    {"orient": "left", "grid": true, "scale": "yscale", "tickSize": 0, "labelPadding": 4, "zindex": 1},
    {"orient": "bottom", "grid": true, "scale": "xscale"}
  ],
  "legends": [
    {
      "title": "Projects",
      "stroke": "color",
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
    },
    {
      "title": "Branches",
      "stroke": "branchColor",
      "fill": "branchColor",
      "strokeColor": "#ccc",
      "padding": 8,
      "cornerRadius": 4,
      "symbolLimit": 50,
      "labelLimit": 300,
      "encode": {
        "symbols": {
          "name": "branchLegendSymbol",
          "interactive": true,
          "update": {
            "strokeWidth": {"value": 2},
            "opacity": [
              {
                "comment": "here `datum` is `selectedBranch` data set",
                "test": "!length(data('selectedBranch')) || indata('selectedBranch', 'value', datum.value)",
                "value": 0.7
              },
              {"value": 0.15}
            ],
            "size": {"value": 64}
          }
        },
        "labels": {
          "name": "branchLegendLabel",
          "interactive": true,
          "update": {
            "opacity": [
              {
                "comment": "here `datum` is `selectedBranch` data set",
                "test": "!length(data('selectedBranch')) || indata('selectedBranch', 'value', datum.value)",
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
              "fill": {"scale": "color", "field": "benchmark"},
              "opacity": [
                {
                  "test": "(!length(data('selected')) || indata('selected', 'value', datum.benchmark))",
                  "value": 0.95
                },
                {"value": 0.15}
              ]
            },
            "update": {
  "opacity": [
                {
                  "test": "(!length(data('selected')) || indata('selected', 'value', datum.benchmark))",
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
