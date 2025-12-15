API Summary — Modules & Endpoints

1) Topic Cooccurrence
- Purpose: Count co-occurrence between topics/tags in a time window.
- Endpoint: GET /api/cooccurrence
- Params:
  - startDate (yyyy-MM-dd) optional, default 2008-01-01
  - endDate (yyyy-MM-dd) optional, default today
  - topN (int) optional, default 10
  - metric (string) optional, default "count"
- Example:
  curl "http://localhost:8080/api/cooccurrence?startDate=2015-01-01&endDate=2025-12-15&topN=20&metric=count"

2) Topic Trend
- Purpose: Time-series trends per topic (count, score, views, answers). Supports compare/integrate/fixedMetric and month/quarter/year aggregation.
- Endpoints:
  - GET /api/trend (direct API)
  - POST /trend/data (page form-data)
- Key params:
  - topicIds (comma or multi) — if empty, all topics
  - keywords / selectedKeywords (comma) — optional, used to intersect with topic.relatedKeywords
  - scopes (comma) — fields to search, default: tag,title,fulltext
  - chartType: line|pie (default line)
  - mode: compare|integrate|fixedMetric (default compare)
  - fixedMetric: count|score|views|answers (default count)
  - startDate / endDate (yyyy-MM-dd), default 2008-01-01 to today
  - granularity: month|quarter|year (default quarter)
- Response (line): { dates, series, normalizedSeries, seriesMap }
  - dates: ordered time labels
  - series: Map<string, List<Number>> original values
  - normalizedSeries: Map<string, List<Double>> 0..1 (backend-normalized)
  - seriesMap: Map<topicName, List<seriesKeys>> (helps mapping)
- Example GET:
  curl "http://localhost:8080/api/trend?topicIds=1,2&keywords=java,jvm&scopes=tag,title&chartType=line&mode=compare&fixedMetric=count&startDate=2018-01-01&endDate=2025-12-15&granularity=quarter"
- Example POST (PowerShell):
  ```powershell
  Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/trend/data' -Body @{
    topicIds = '1,2';
    selectedKeywords = 'java,jvm';
    scopes = 'tag,title';
    chartType = 'line';
    mode = 'compare';
    fixedMetric = 'count';
    granularity = 'quarter';
    startDate = '2008-01-01';
    endDate = '2025-12-15'
  }
  ```

3) Solvable Questions
- Purpose: Compare how easily questions in a topic get answered.
- Endpoint: GET /api/solvable
- Params:
  - startDate / endDate (yyyy-MM-dd)
  - topicId (optional)
- Example:
  curl "http://localhost:8080/api/solvable?startDate=2019-01-01&endDate=2025-12-15&topicId=3"

4) Multithreading Pitfalls
- Purpose: Count occurrences of common multithreading pitfalls (deadlock, race condition, etc.) and custom keywords.
- Endpoint: GET /api/pitfall
- Params:
  - startDate / endDate (yyyy-MM-dd)
  - pitfalls (comma) optional, uses defaults if empty
  - customWords (comma) optional
  - lineChartAttribute: count|views|answers|score (default count)
- Example:
  curl "http://localhost:8080/api/pitfall?startDate=2020-01-01&endDate=2025-12-15&pitfalls=Deadlock,Race%20Condition&lineChartAttribute=count"

Notes and quick tips
- Default date range: 2008-01-01 to today when not provided.
- Keyword matching uses "Containing" queries (fuzzy). For exact tag matching, adjust repository queries.
- Trend endpoint returns both raw `series` and `normalizedSeries` plus `seriesMap`.
- If counts look low, enable debug logs to inspect `effectiveKeywords` and per-topic query results.
