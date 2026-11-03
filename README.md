# TV Builds

## Secret scanning

A `pre-push` hook runs [gitleaks](https://github.com/gitleaks/gitleaks) on the commits you're pushing and blocks the push if it finds likely secrets (values are always redacted in output).

**One-time setup:** install gitleaks, then run `sh scripts/setup-githooks.sh` (or `git config core.hooksPath .githooks`).

**False positive?** Add a regex or path to `[allowlist]` in `.gitleaks.toml`, or append a `# gitleaks:allow` comment to the line.

**Emergency bypass:** `SKIP_SECRET_SCAN=1 git push` (prints a warning; rotate anything real that slips through).

Repo-specific extra checks can go in `.githooks/pre-push.local` (executable); it runs after the scan.
