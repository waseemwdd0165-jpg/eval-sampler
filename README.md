# eval-sampler

Reproducible stratified sampling for evaluation queues, in Java. No build tool,
no dependencies: a JDK is the whole toolchain.

You have 40,000 model responses and budget to rate 500. Which 500? Take the
first 500 and you rate a week of traffic. Shuffle and take 500 and you get
almost no Bhojpuri, no high-risk prompts, and nothing to compare next month's
sample against. Then the budget goes up to 1,200 and the obvious fix, redrawing,
throws away every rating already done.

## What it does

```
sh build.sh
java -cp out com.waseemansari.evalsampler.Main sample/responses.csv \
     --size 60 --seed queue-2026-07 --min 3 --out picked.csv
```

```
sample/responses.csv  400 rows, stratified on language, seed 'queue-2026-07'

  language               population    quota    drawn
  bho                             5        3        3
  en                            250       35       35
  hi                             90       13       13
  mr                             40        6        6
  ur                             15        3        3

  60 row(s) drawn
  written to picked.csv
```

| Option | |
|---|---|
| `--size N` | rows to draw |
| `--stratum COL` | column to stratify on (default: the second column) |
| `--id COL` | column holding the row id (default: `id`) |
| `--seed TEXT` | anything; the same text redraws the same rows |
| `--min N` | smallest worthwhile look at any one stratum |
| `--out FILE` | write the drawn rows, with the input's own columns |

## The two properties that matter

**Reproducible.** Nothing is shuffled. Each row is given a sort key from the
seed and its own id, and the sample is the lowest keys in each stratum. The same
seed and the same ids give the same sample on any machine, whatever order the
export happens to arrive in. A second rater can be handed a seed rather than a
file. Two months can be compared because the difference is the data, not the
draw.

**Nested.** Ask for 1,200 after rating 500 and the 1,200 contain those 500, in
the same relative order. The keys did not move, so a larger sample reaches
further down the same ordered list. The alternative, sampling the leftovers
separately, quietly biases the top-up: rows already rated are no longer
eligible, so the second draw is not a sample of the population any more.

The key is the first eight bytes of `SHA-256(seed, 0x00, id)`. `String.hashCode`
would be reproducible too, but it clusters on ids that share a prefix, which is
what real ids look like. A published digest also means a script in Python or Go
can pick the same rows.

## Quotas

Three things have to hold at once, and proportional allocation on its own gives
none of them:

1. **The quotas add up to exactly n.** Rounding each share independently loses
   or gains rows. Largest-remainder allocation hands the leftovers to whoever
   was cut by the most, with deterministic tie-breaks so the answer does not
   depend on map ordering.
2. **No stratum is asked for more rows than it has.**
3. **A small stratum is still looked at.** In the sample file Bhojpuri is 1.25%
   of the population, so its proportional share of 60 rows is 0.75 and it rounds
   to nothing. `--min 3` lifts it to three and takes the difference back off the
   largest strata. An impossible minimum is refused with the arithmetic in the
   message rather than silently ignored.

The drawn rows come out interleaved rather than grouped by stratum, so a rater
working down the file does not do all of one language first and land the fatigue
on one stratum.

## Tests

```
sh build.sh          # or build.cmd on Windows
```

compiles and runs them. **28 tests, all passed** on OpenJDK 11.

They cover the allocation arithmetic (including a sweep of 1,800 combinations of
size and minimum, checking every one sums correctly and over-draws nothing), the
reproducibility and nesting properties, the refusals, and the CSV reader against
commas, doubled quotes and newlines inside quoted fields. The sort key is
checked against a SHA-256 digest worked out independently rather than against
whatever the code happens to return.

## Layout

```
src/com/waseemansari/evalsampler/
    Allocation.java          how many rows each stratum gets
    StratifiedSampler.java   the draw itself
    Csv.java                 reader and writer, no dependency
    Item.java  SampleResult.java  Main.java
test/com/waseemansari/evalsampler/
    Check.java               a twenty line test harness
    Tests.java
sample/responses.csv         400 rows across five languages
```
