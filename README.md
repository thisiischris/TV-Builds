# TV Builds

## Secret scanning (pre-push)

A `pre-push` hook runs [gitleaks](https://github.com/gitleaks/gitleaks) **8.30.1** on only the commits you're pushing and blocks the push if it finds likely secrets (values are always redacted). Any other gitleaks version is rejected.

**One-time setup:** `sh scripts/setup-githooks.sh` — sets `core.hooksPath=.githooks` and, if needed, downloads gitleaks 8.30.1 from the official GitHub release, verifies its pinned SHA-256 and installs it to `~/.local/bin` (override with `GITLEAKS_INSTALL_DIR`; `SKIP_GITLEAKS_INSTALL=1` sets only the hooks path).

**False positive?** Add a narrow, anchored *value* regex to `[allowlist] regexes` in `.gitleaks.toml` (or a `gitleaks:allow` comment on the line). Never allowlist paths — gitleaks skips those files entirely. `.gitleaks.toml` is the vendored gitleaks v8.30.1 default config with its global path allowlist removed, so lockfiles (`package-lock.json`, `yarn.lock`, `go.sum`, …), `node_modules/` and `.env.example` are scanned too; when bumping the pinned gitleaks version, re-vendor it (see the header in that file).

**Emergency bypass:** `SKIP_SECRET_SCAN=1 git push` (prints a warning; rotate anything real that slips through).

**Self-test:** `sh scripts/test-secret-scan-hook.sh` (scratch repo; prints PASS/FAIL per case).
