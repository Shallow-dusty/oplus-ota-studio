# OPlus OTA Studio — Project Instructions

This file is loaded automatically by ZCode in every session working in this repo.
Authoritative spec lives in `docs/superpowers/specs/2026-06-29-oplus-ota-studio-design.md`.

## Git Commit Strategy

**Goal: rollback safety.** Optimize commits for *revertability*, not for chunking work into big batches.

### Granularity

- Prefer **many small, logically independent commits** over one large commit. ("高粒度" in this project = high-resolution, individually-revertable commits.)
- Each commit must be a **single self-contained change**: one feature, one fix, one refactor, or one doc update. If a change spans unrelated concerns, split it into N commits before staging.
- Never mix these in the same commit:
  - behavioral change + formatting/reorder churn
  - feature + unrelated refactor
  - code + unrelated doc edits
  - dependency bump + feature using it (bump first, separately)
- A good test: could a reviewer `git revert <sha>` this one commit without breaking the build or pulling in unrelated changes? If not, split further.

### Messages

- Conventional Commits: `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `perf:`, `ci:`, `chore:`, `build:`.
- Subject ≤ 72 chars, imperative mood, no trailing period.
- Body explains *why* (rationale, trade-off, rollback impact); the diff already shows *what*.
- Reference the spec section when relevant, e.g. `docs: expand §3 download state machine`.

### Branching

- `main` is the protected trunk. Default to feature branches: `feat/`, `fix/`, `docs/`, `chore/`, `ci/`.
- Squash-merge PRs so each merged logical change maps to exactly one commit on `main`.
- **Exception:** initial project bootstrap (scaffolding, first docs, CI setup) may land on `main` directly until regular feature work begins.

### Staging discipline

- Stage with explicit paths (`git add <files>`), not `git add -A`, so unrelated edits don't leak into a commit.
- Before committing, run `git status` + `git diff --cached --stat` to confirm the staged set is exactly one logical unit.
- If `git status` shows unrelated changes, leave them unstaged for a later commit.
