// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

{
  "note": "May https://vega.github.io/vega/docs/ be with you",
  "$schema": "https://vega.github.io/schema/vega/v4.3.0.json",
  "description": "FindUsage - CompilerReferenceIndex",
  "title": "FindUsage - CompilerReferenceIndex",
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
    },
    {"name": "timestamp", "value": true, "bind": {"input": "checkbox"}}
  ],
  "data": [
    {
      "name": "table",
      "comment": "To test chart in VEGA editor https://vega.github.io/editor/#/ change `_values` to `values` and rename `url` property",
      "_values": {
        "aggregations" : {
          "benchmark" : {
            "doc_count_error_upper_bound" : 0,
            "sum_other_doc_count" : 0,
            "buckets" : [
              {
                "key" : "findUsages50_50",
                "doc_count" : 7,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : "213",
                      "doc_count" : 4,
                      "name" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : "findUsages pkg1/DataClass.kt",
                            "doc_count" : 4,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-29T01:00:00.000Z",
                                  "key" : 1635469200000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 56.0
                                  },
                                  "avgValue" : {
                                    "value" : 2450.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146296951,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T02:00:00.000Z",
                                  "key" : 1635472800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T03:00:00.000Z",
                                  "key" : 1635476400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T04:00:00.000Z",
                                  "key" : 1635480000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T05:00:00.000Z",
                                  "key" : 1635483600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T06:00:00.000Z",
                                  "key" : 1635487200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T07:00:00.000Z",
                                  "key" : 1635490800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T08:00:00.000Z",
                                  "key" : 1635494400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T09:00:00.000Z",
                                  "key" : 1635498000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T10:00:00.000Z",
                                  "key" : 1635501600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T11:00:00.000Z",
                                  "key" : 1635505200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T12:00:00.000Z",
                                  "key" : 1635508800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T13:00:00.000Z",
                                  "key" : 1635512400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T14:00:00.000Z",
                                  "key" : 1635516000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T15:00:00.000Z",
                                  "key" : 1635519600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T16:00:00.000Z",
                                  "key" : 1635523200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T17:00:00.000Z",
                                  "key" : 1635526800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T18:00:00.000Z",
                                  "key" : 1635530400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T19:00:00.000Z",
                                  "key" : 1635534000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T20:00:00.000Z",
                                  "key" : 1635537600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T21:00:00.000Z",
                                  "key" : 1635541200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T22:00:00.000Z",
                                  "key" : 1635544800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T23:00:00.000Z",
                                  "key" : 1635548400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T00:00:00.000Z",
                                  "key" : 1635552000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T01:00:00.000Z",
                                  "key" : 1635555600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T02:00:00.000Z",
                                  "key" : 1635559200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T03:00:00.000Z",
                                  "key" : 1635562800000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 99.0
                                  },
                                  "avgValue" : {
                                    "value" : 2566.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146505614,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T04:00:00.000Z",
                                  "key" : 1635566400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T05:00:00.000Z",
                                  "key" : 1635570000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T06:00:00.000Z",
                                  "key" : 1635573600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T07:00:00.000Z",
                                  "key" : 1635577200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T08:00:00.000Z",
                                  "key" : 1635580800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T09:00:00.000Z",
                                  "key" : 1635584400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T10:00:00.000Z",
                                  "key" : 1635588000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T11:00:00.000Z",
                                  "key" : 1635591600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T12:00:00.000Z",
                                  "key" : 1635595200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T13:00:00.000Z",
                                  "key" : 1635598800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T14:00:00.000Z",
                                  "key" : 1635602400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T15:00:00.000Z",
                                  "key" : 1635606000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T16:00:00.000Z",
                                  "key" : 1635609600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T17:00:00.000Z",
                                  "key" : 1635613200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T18:00:00.000Z",
                                  "key" : 1635616800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T19:00:00.000Z",
                                  "key" : 1635620400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T20:00:00.000Z",
                                  "key" : 1635624000000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 109.0
                                  },
                                  "avgValue" : {
                                    "value" : 2825.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146598969,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T21:00:00.000Z",
                                  "key" : 1635627600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T22:00:00.000Z",
                                  "key" : 1635631200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T23:00:00.000Z",
                                  "key" : 1635634800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T00:00:00.000Z",
                                  "key" : 1635638400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T01:00:00.000Z",
                                  "key" : 1635642000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T02:00:00.000Z",
                                  "key" : 1635645600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T03:00:00.000Z",
                                  "key" : 1635649200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T04:00:00.000Z",
                                  "key" : 1635652800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T05:00:00.000Z",
                                  "key" : 1635656400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T06:00:00.000Z",
                                  "key" : 1635660000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T07:00:00.000Z",
                                  "key" : 1635663600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T08:00:00.000Z",
                                  "key" : 1635667200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T09:00:00.000Z",
                                  "key" : 1635670800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T10:00:00.000Z",
                                  "key" : 1635674400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T11:00:00.000Z",
                                  "key" : 1635678000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T12:00:00.000Z",
                                  "key" : 1635681600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T13:00:00.000Z",
                                  "key" : 1635685200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T14:00:00.000Z",
                                  "key" : 1635688800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T15:00:00.000Z",
                                  "key" : 1635692400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T16:00:00.000Z",
                                  "key" : 1635696000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T17:00:00.000Z",
                                  "key" : 1635699600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T18:00:00.000Z",
                                  "key" : 1635703200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T19:00:00.000Z",
                                  "key" : 1635706800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T20:00:00.000Z",
                                  "key" : 1635710400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T21:00:00.000Z",
                                  "key" : 1635714000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T22:00:00.000Z",
                                  "key" : 1635717600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T23:00:00.000Z",
                                  "key" : 1635721200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-11-01T00:00:00.000Z",
                                  "key" : 1635724800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-11-01T01:00:00.000Z",
                                  "key" : 1635728400000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 123.0
                                  },
                                  "avgValue" : {
                                    "value" : 2855.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146667629,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                }
                              ],
                              "interval" : "1h"
                            }
                          }
                        ]
                      }
                    },
                    {
                      "key" : "master",
                      "doc_count" : 3,
                      "name" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : "findUsages pkg1/DataClass.kt",
                            "doc_count" : 3,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-30T06:00:00.000Z",
                                  "key" : 1635573600000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 108.0
                                  },
                                  "avgValue" : {
                                    "value" : 2407.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146505669,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "master",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T07:00:00.000Z",
                                  "key" : 1635577200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T08:00:00.000Z",
                                  "key" : 1635580800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T09:00:00.000Z",
                                  "key" : 1635584400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T10:00:00.000Z",
                                  "key" : 1635588000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T11:00:00.000Z",
                                  "key" : 1635591600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T12:00:00.000Z",
                                  "key" : 1635595200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T13:00:00.000Z",
                                  "key" : 1635598800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T14:00:00.000Z",
                                  "key" : 1635602400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T15:00:00.000Z",
                                  "key" : 1635606000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T16:00:00.000Z",
                                  "key" : 1635609600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T17:00:00.000Z",
                                  "key" : 1635613200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T18:00:00.000Z",
                                  "key" : 1635616800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T19:00:00.000Z",
                                  "key" : 1635620400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T20:00:00.000Z",
                                  "key" : 1635624000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T21:00:00.000Z",
                                  "key" : 1635627600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T22:00:00.000Z",
                                  "key" : 1635631200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T23:00:00.000Z",
                                  "key" : 1635634800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T00:00:00.000Z",
                                  "key" : 1635638400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T01:00:00.000Z",
                                  "key" : 1635642000000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 167.0
                                  },
                                  "avgValue" : {
                                    "value" : 2981.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146599305,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "master",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T02:00:00.000Z",
                                  "key" : 1635645600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T03:00:00.000Z",
                                  "key" : 1635649200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T04:00:00.000Z",
                                  "key" : 1635652800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T05:00:00.000Z",
                                  "key" : 1635656400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T06:00:00.000Z",
                                  "key" : 1635660000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T07:00:00.000Z",
                                  "key" : 1635663600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T08:00:00.000Z",
                                  "key" : 1635667200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T09:00:00.000Z",
                                  "key" : 1635670800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T10:00:00.000Z",
                                  "key" : 1635674400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T11:00:00.000Z",
                                  "key" : 1635678000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T12:00:00.000Z",
                                  "key" : 1635681600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T13:00:00.000Z",
                                  "key" : 1635685200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T14:00:00.000Z",
                                  "key" : 1635688800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T15:00:00.000Z",
                                  "key" : 1635692400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T16:00:00.000Z",
                                  "key" : 1635696000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T17:00:00.000Z",
                                  "key" : 1635699600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T18:00:00.000Z",
                                  "key" : 1635703200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T19:00:00.000Z",
                                  "key" : 1635706800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T20:00:00.000Z",
                                  "key" : 1635710400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T21:00:00.000Z",
                                  "key" : 1635714000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T22:00:00.000Z",
                                  "key" : 1635717600000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 102.0
                                  },
                                  "avgValue" : {
                                    "value" : 2684.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146667414,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "master",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                }
                              ],
                              "interval" : "1h"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              },
              {
                "key" : "findUsages50_50_with_cri",
                "doc_count" : 7,
                "branch" : {
                  "doc_count_error_upper_bound" : 0,
                  "sum_other_doc_count" : 0,
                  "buckets" : [
                    {
                      "key" : "213",
                      "doc_count" : 4,
                      "name" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : "findUsages pkg1/DataClass.kt",
                            "doc_count" : 4,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-29T01:00:00.000Z",
                                  "key" : 1635469200000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 55.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146296951,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T02:00:00.000Z",
                                  "key" : 1635472800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T03:00:00.000Z",
                                  "key" : 1635476400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T04:00:00.000Z",
                                  "key" : 1635480000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T05:00:00.000Z",
                                  "key" : 1635483600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T06:00:00.000Z",
                                  "key" : 1635487200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T07:00:00.000Z",
                                  "key" : 1635490800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T08:00:00.000Z",
                                  "key" : 1635494400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T09:00:00.000Z",
                                  "key" : 1635498000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T10:00:00.000Z",
                                  "key" : 1635501600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T11:00:00.000Z",
                                  "key" : 1635505200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T12:00:00.000Z",
                                  "key" : 1635508800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T13:00:00.000Z",
                                  "key" : 1635512400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T14:00:00.000Z",
                                  "key" : 1635516000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T15:00:00.000Z",
                                  "key" : 1635519600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T16:00:00.000Z",
                                  "key" : 1635523200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T17:00:00.000Z",
                                  "key" : 1635526800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T18:00:00.000Z",
                                  "key" : 1635530400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T19:00:00.000Z",
                                  "key" : 1635534000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T20:00:00.000Z",
                                  "key" : 1635537600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T21:00:00.000Z",
                                  "key" : 1635541200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T22:00:00.000Z",
                                  "key" : 1635544800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-29T23:00:00.000Z",
                                  "key" : 1635548400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T00:00:00.000Z",
                                  "key" : 1635552000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T01:00:00.000Z",
                                  "key" : 1635555600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T02:00:00.000Z",
                                  "key" : 1635559200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T03:00:00.000Z",
                                  "key" : 1635562800000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 53.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146505614,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T04:00:00.000Z",
                                  "key" : 1635566400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T05:00:00.000Z",
                                  "key" : 1635570000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T06:00:00.000Z",
                                  "key" : 1635573600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T07:00:00.000Z",
                                  "key" : 1635577200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T08:00:00.000Z",
                                  "key" : 1635580800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T09:00:00.000Z",
                                  "key" : 1635584400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T10:00:00.000Z",
                                  "key" : 1635588000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T11:00:00.000Z",
                                  "key" : 1635591600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T12:00:00.000Z",
                                  "key" : 1635595200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T13:00:00.000Z",
                                  "key" : 1635598800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T14:00:00.000Z",
                                  "key" : 1635602400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T15:00:00.000Z",
                                  "key" : 1635606000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T16:00:00.000Z",
                                  "key" : 1635609600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T17:00:00.000Z",
                                  "key" : 1635613200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T18:00:00.000Z",
                                  "key" : 1635616800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T19:00:00.000Z",
                                  "key" : 1635620400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T20:00:00.000Z",
                                  "key" : 1635624000000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 57.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146598969,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T21:00:00.000Z",
                                  "key" : 1635627600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T22:00:00.000Z",
                                  "key" : 1635631200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T23:00:00.000Z",
                                  "key" : 1635634800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T00:00:00.000Z",
                                  "key" : 1635638400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T01:00:00.000Z",
                                  "key" : 1635642000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T02:00:00.000Z",
                                  "key" : 1635645600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T03:00:00.000Z",
                                  "key" : 1635649200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T04:00:00.000Z",
                                  "key" : 1635652800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T05:00:00.000Z",
                                  "key" : 1635656400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T06:00:00.000Z",
                                  "key" : 1635660000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T07:00:00.000Z",
                                  "key" : 1635663600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T08:00:00.000Z",
                                  "key" : 1635667200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T09:00:00.000Z",
                                  "key" : 1635670800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T10:00:00.000Z",
                                  "key" : 1635674400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T11:00:00.000Z",
                                  "key" : 1635678000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T12:00:00.000Z",
                                  "key" : 1635681600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T13:00:00.000Z",
                                  "key" : 1635685200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T14:00:00.000Z",
                                  "key" : 1635688800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T15:00:00.000Z",
                                  "key" : 1635692400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T16:00:00.000Z",
                                  "key" : 1635696000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T17:00:00.000Z",
                                  "key" : 1635699600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T18:00:00.000Z",
                                  "key" : 1635703200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T19:00:00.000Z",
                                  "key" : 1635706800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T20:00:00.000Z",
                                  "key" : 1635710400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T21:00:00.000Z",
                                  "key" : 1635714000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T22:00:00.000Z",
                                  "key" : 1635717600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T23:00:00.000Z",
                                  "key" : 1635721200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-11-01T00:00:00.000Z",
                                  "key" : 1635724800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-11-01T01:00:00.000Z",
                                  "key" : 1635728400000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 54.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146667629,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "213",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                }
                              ],
                              "interval" : "1h"
                            }
                          }
                        ]
                      }
                    },
                    {
                      "key" : "master",
                      "doc_count" : 3,
                      "name" : {
                        "doc_count_error_upper_bound" : 0,
                        "sum_other_doc_count" : 0,
                        "buckets" : [
                          {
                            "key" : "findUsages pkg1/DataClass.kt",
                            "doc_count" : 3,
                            "values" : {
                              "buckets" : [
                                {
                                  "key_as_string" : "2021-10-30T06:00:00.000Z",
                                  "key" : 1635573600000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 54.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146505669,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "master",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T07:00:00.000Z",
                                  "key" : 1635577200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T08:00:00.000Z",
                                  "key" : 1635580800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T09:00:00.000Z",
                                  "key" : 1635584400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T10:00:00.000Z",
                                  "key" : 1635588000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T11:00:00.000Z",
                                  "key" : 1635591600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T12:00:00.000Z",
                                  "key" : 1635595200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T13:00:00.000Z",
                                  "key" : 1635598800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T14:00:00.000Z",
                                  "key" : 1635602400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T15:00:00.000Z",
                                  "key" : 1635606000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T16:00:00.000Z",
                                  "key" : 1635609600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T17:00:00.000Z",
                                  "key" : 1635613200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T18:00:00.000Z",
                                  "key" : 1635616800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T19:00:00.000Z",
                                  "key" : 1635620400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T20:00:00.000Z",
                                  "key" : 1635624000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T21:00:00.000Z",
                                  "key" : 1635627600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T22:00:00.000Z",
                                  "key" : 1635631200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-30T23:00:00.000Z",
                                  "key" : 1635634800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T00:00:00.000Z",
                                  "key" : 1635638400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T01:00:00.000Z",
                                  "key" : 1635642000000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 55.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146599305,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "master",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T02:00:00.000Z",
                                  "key" : 1635645600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T03:00:00.000Z",
                                  "key" : 1635649200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T04:00:00.000Z",
                                  "key" : 1635652800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T05:00:00.000Z",
                                  "key" : 1635656400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T06:00:00.000Z",
                                  "key" : 1635660000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T07:00:00.000Z",
                                  "key" : 1635663600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T08:00:00.000Z",
                                  "key" : 1635667200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T09:00:00.000Z",
                                  "key" : 1635670800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T10:00:00.000Z",
                                  "key" : 1635674400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T11:00:00.000Z",
                                  "key" : 1635678000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T12:00:00.000Z",
                                  "key" : 1635681600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T13:00:00.000Z",
                                  "key" : 1635685200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T14:00:00.000Z",
                                  "key" : 1635688800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T15:00:00.000Z",
                                  "key" : 1635692400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T16:00:00.000Z",
                                  "key" : 1635696000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T17:00:00.000Z",
                                  "key" : 1635699600000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T18:00:00.000Z",
                                  "key" : 1635703200000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T19:00:00.000Z",
                                  "key" : 1635706800000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T20:00:00.000Z",
                                  "key" : 1635710400000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T21:00:00.000Z",
                                  "key" : 1635714000000,
                                  "doc_count" : 0,
                                  "avgError" : {
                                    "value" : null
                                  },
                                  "avgValue" : {
                                    "value" : null
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [ ]
                                  }
                                },
                                {
                                  "key_as_string" : "2021-10-31T22:00:00.000Z",
                                  "key" : 1635717600000,
                                  "doc_count" : 1,
                                  "avgError" : {
                                    "value" : 0.0
                                  },
                                  "avgValue" : {
                                    "value" : 55.0
                                  },
                                  "buildId" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : 146667414,
                                        "doc_count" : 1
                                      }
                                    ]
                                  },
                                  "branch" : {
                                    "doc_count_error_upper_bound" : 0,
                                    "sum_other_doc_count" : 0,
                                    "buckets" : [
                                      {
                                        "key" : "master",
                                        "doc_count" : 1
                                      }
                                    ]
                                  }
                                }
                              ],
                              "interval" : "1h"
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
        //"comment": "source index pattern",
        "index": "kotlin_ide_benchmarks*",
        //"comment": "it's a body of ES _search query to check query place it into `POST /kotlin_ide_benchmarks*/_search`",
        //"comment": "it uses Kibana specific %timefilter% for time frame selection",
        "body": {
          "size": 0,
          "query": {
            "bool": {
              "must": [
                {
                  "bool": {
                    "must_not": [
                       {"exists": {"field": "warmUp"}},
                       {"exists": {"field": "synthetic"}}
                     ]
                   }
                },
                {"prefix": {"benchmark.keyword": "findUsages"}},
                {"range": {"buildTimestamp": {"%timefilter%": true}}}
              ]
            }
          },
          "aggs": {
            "benchmark": {
              "terms": {
                "field": "benchmark.keyword",
                "size": 10
              },
              "aggs": {
                "branch": {
                  "terms": {
                    "size": 10,
                    "field": "buildBranch.keyword"
                  },

                  "aggs": {
                    "name": {
                      "terms": {
                        "field": "name.keyword",
                        "size": 500
                      },
                      "aggs": {
                        "values": {
                          "auto_date_histogram": {
                              "buckets": 500,
                              "field": "buildTimestamp",
                              "minimum_interval": "hour"
                          },
                          "aggs": {
                            "buildId": {
                              "terms": {
                                "size": 1,
                                "field": "buildId"
                              }
                            },
                            "branch": {
                              "terms": {
                                "size": 1,
                                "field": "buildBranch.keyword"
                              }
                            },
                            "avgValue":{
                              "avg": {
                                "field": "metricValue"
                              }
                            },
                            "avgError":{
                              "avg": {
                                "field": "metricError"
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
      "comment": "we need to have follow data: \"buildId\", \"metricName\", \"metricValue\" and \"metricError\"",
      "comment": "so it has to be array of {\"buildId\": \"...\", \"metricName\": \"...\", \"metricValue\": ..., \"metricError\": ...}",
      "transform": [
        {"type": "project", "fields": ["benchmark"]},
        {"type": "flatten", "fields": ["benchmark.buckets"], "as": ["benchmark_buckets"]},
        {"type": "project", "fields": ["benchmark_buckets.key", "benchmark_buckets.branch"], "as": ["benchmark", "benchmark_buckets_branch"]},
        {"type": "flatten", "fields": ["benchmark_buckets_branch.buckets"], "as": ["branch_buckets"]},
        {"type": "project", "fields": ["benchmark", "branch_buckets.key", "branch_buckets.name"], "as": ["benchmark", "branch", "branch_name_values"]},
        {"type": "flatten", "fields": ["branch_name_values.buckets"], "as": ["name_buckets"]},
        {"type": "project", "fields": ["benchmark", "branch", "name_buckets.key", "name_buckets.values"], "as": ["benchmark", "branch", "name", "name_values"]},
        {"type": "flatten", "fields": ["name_values.buckets"], "as": ["name_values_buckets"]},
        {"type": "project", "fields": ["benchmark", "branch", "name", "name_values_buckets.key", "name_values_buckets.key_as_string", "name_values_buckets.avgError", "name_values_buckets.avgValue", "name_values_buckets.buildId.buckets"], "as": ["benchmark", "branch", "case_name", "buildTimestamp", "timestamp_value", "avgError", "avgValue", "buildId_buckets"]},
        {"type": "formula", "as": "metricName", "expr": "replace(datum.benchmark, /findUsages(.*)/, '$1') + ' ' + replace(datum.case_name, /findUsages(.*)/, '$1')"},
        {"type": "formula", "as": "metricError", "expr": "datum.avgError.value"},
        {"type": "formula", "as": "metricValue", "expr": "datum.avgValue.value"},
        {"type": "flatten", "fields": ["buildId_buckets"], "as": ["buildId_values"]},
        {"type": "formula", "as": "buildId", "expr": "datum.buildId_values.key"},
        {
          "type": "formula",
          "as": "timestamp",
          "expr": "timeFormat(toDate(datum.buildTimestamp), '%Y-%m-%d %H:%M')"
        },
        {
          "comment": "create `url` value that points to TC build",
          "type": "formula",
          "as": "url",
          "expr": "'https://buildserver.labs.intellij.net/buildConfiguration/Kotlin_Benchmarks_PluginPerformanceTests_IdeaPluginPerformanceTests/' + datum.buildId"
        },
        {"type": "collect","sort": {"field": "timestamp"}}
      ]
    },
    {
      "name": "selected",
      "on": [
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
  "axes": [
    {
      "scale": "x",
      "grid": true,
      "domain": false,
      "orient": "bottom",
      "labelAngle": -20,
      "labelAlign": "right",
      "title": {"signal": "timestamp ? 'timestamp' : 'buildId'"},
      "titlePadding": 10,
      "tickCount": 5,
      "encode": {
        "labels": {
          "interactive": true,
          "update": {"tooltip": {"signal": "datum.label"}}
        }
      }
    },
    {
      "scale": "y",
      "grid": true,
      "domain": false,
      "orient": "left",
      "titlePadding": 10,
      "title": "ms",
      "titleAnchor": "end",
      "titleAngle": 0
    }
  ],
  "scales": [
    {
      "name": "x",
      "type": "point",
      "range": "width",
      "domain": {"data": "table", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}}
    },
    {
      "name": "y",
      "type": "linear",
      "range": "height",
      "nice": true,
      "zero": true,
      "domain": {"data": "table", "field": "metricValue"}
    },
    {
      "name": "color",
      "type": "ordinal",
      "range": "category",
      "domain": {"data": "table", "field": "metricName"}
    },
    {
      "name": "size",
      "type": "linear",
      "round": true,
      "nice": false,
      "zero": true,
      "domain": {"data": "table", "field": "metricError"},
      "range": [1, 100]
    },
    {
      "name": "branchColor",
      "type": "ordinal",
      "domain": {"data": "table", "field": "branch"},
      "range": {"scheme": "dark2"}
    }
  ],
  "legends": [
    {
      "title": "Cases",
      "stroke": "color",
      "strokeColor": "#ccc",
      "padding": 8,
      "cornerRadius": 4,
      "symbolLimit": 50,
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
        "facet": {"name": "series", "data": "table", "groupby": "metricName"}
      },
      "marks": [
        {
          "type": "text",
          "from": {"data": "series"},
          "encode": {
            "update": {
              "x": {"scale": "x", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}},
              "align": {"value": "center"},
              "y": {"value": -10},
              "angle": {"value": 90},
              "fill": {"value": "#000"},
              "text": [{"test": "datum.branch != 'master'", "field": "branch"}, {"value": ""}],
              "fontSize": {"value": 10},
              "font": {"value": "monospace"}
            }
          }
        },
        {
          "type": "rect",
          "from": {"data": "series"},
          "encode": {
            "update": {
              "x": {"scale": "x", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}, "offset":-5},
              "x2": {"scale": "x", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}, "offset": 5},
              "y": {"value": 0},
              "y2": {"signal": "height"},
              "fill": [{"test": "datum.branch != 'master'", "scale": "branchColor", "field": "branch"}, {"value": ""}],
              "opacity": [
                  {
                    "test": "(!domain || inrange(datum.branch, domain)) && (!length(data('selectedBranch')) || indata('selectedBranch', 'value', datum.branch))",
                    "value": 0.1
                  },
                  {"value": 0.01}
                ]
            }
          }
        },
        {
          "type": "line",
          "from": {"data": "series"},
          "encode": {
            "hover": {"opacity": {"value": 1}, "strokeWidth": {"value": 4}},
            "update": {
              "x": {"scale": "x", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}},
              "y": {"scale": "y", "field": "metricValue"},
              "strokeWidth": {"value": 2},
              "opacity": [
                {
                  "test": "(!domain || inrange(datum.buildId, domain)) && (!length(data('selected')) || indata('selected', 'value', datum.metricName))",
                  "value": 0.7
                },
                {"value": 0.15}
              ],
              "stroke": [
                {
                  "test": "(!domain || inrange(datum.buildId, domain)) && (!length(data('selected')) || indata('selected', 'value', datum.metricName))",
                  "scale": "color",
                  "field": "metricName"
                },
                {"value": "#ccc"}
              ]
            }
          }
        },
        {
          "type": "symbol",
          "from": {"data": "series"},
          "encode": {
            "enter": {
              "fill": {"value": "#B00"},
              "size": [{ "test": "datum.hasError", "value": 250 }, {"value": 0}],
              "shape": {"value": "cross"},
              "angle": {"value": 45},
              "x": {"scale": "x", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}},
              "y": {"scale": "y", "field": "metricValue"},
              "strokeWidth": {"value": 1},
              "opacity": [
                {
                  "test": "(!domain || inrange(datum.buildId, domain)) && datum.hasError && (!length(data('selected')) || indata('selected', 'value', datum.metricName))",
                  "value": 1
                },
                {"value": 0.15}
              ]
            },
            "update": {
              "opacity": [
                {
                  "test": "(!domain || inrange(datum.buildId, domain))  && datum.hasError && (!length(data('selected')) || indata('selected', 'value', datum.metricName))",
                  "value": 1
                },
                {"value": 0.15}
              ]
            }
          },
          "zindex": 1
        },
        {
          "type": "symbol",
          "from": {"data": "series"},
          "encode": {
            "enter": {
              "tooltip": {
                "signal": "datum.metricName + ': ' + datum.metricValue + ' ms'"
              },
              "href": {"field": "url"},
              "cursor": {"value": "pointer"},
              "size": {"scale": "size", "field": "metricError"},
              "x": {"scale": "x", "field": {"signal": "timestamp ? 'timestamp' : 'buildId'"}},
              "y": {"scale": "y", "field": "metricValue"},
              "strokeWidth": {"value": 1},
              "fill": {"scale": "color", "field": "metricName"}
            },
            "update": {
              "opacity": [
                {
                  "test": "(!domain || inrange(datum.buildId, domain)) && (!length(data('selected')) || indata('selected', 'value', datum.metricName))",
                  "value": 1
                },
                {"value": 0.15}
              ]
            }
          },
          "zindex": 2
        }
      ]
    }
  ]
}
