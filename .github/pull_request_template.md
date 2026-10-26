<!-- For a results submission keep this checklist; for code changes, describe what changed and how you tested it. -->

**Machine:** <!-- CPU + GPU(s), e.g. Ryzen 5 5600 + RX 6600 -->

- [ ] Ran `./suite.sh` unmodified (GPUS used: ...; REPEAT: ...)
- [ ] Closed background apps, laptop on AC (or noted otherwise in `system.md`)
- [ ] Did not touch keyboard or mouse during the runs
- [ ] `python3 tools/analyze.py results/raw/*-suite-*.log` shows every run complete
- [ ] Exported with `tools/export.py`, filled in the Notes in `system.md`, checked it holds nothing private
- [ ] Re-ran `tools/aggregate.py` and committed `RESULTS.md`
