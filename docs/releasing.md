# Releasing

A release is created by pushing a version tag. The workflow [`release.yml`](../.github/workflows/release.yml) builds, tests and publishes the GitHub release.

## Prerequisites

- Push access to the repository.
- The commit to release is on `main` and the [CI](../.github/workflows/ci.yml) run for it is green. The release workflow does not check the branch.

## Create a release

```
git checkout main
git pull
git tag v1.0.0
git push origin v1.0.0
```

Tag names:

| Tag | Result |
|---|---|
| `vX.Y.Z` | Release |
| `vX.Y.Z-<suffix>`, e.g. `v1.0.0-rc1` | Release marked as pre-release |

Tags that do not match `v[0-9]+.[0-9]+.[0-9]+*` do not start the workflow.

## What the workflow does

1. Checks out the tagged commit and sets up Temurin 21.
2. Runs `./mvnw verify`. A failing test aborts the run and no release is created.
3. Packages `wikidata-neo4j-batchimporter-X.Y.Z.zip` with `tools.jar`, `wikidata-import.sh` and `README.md`, and writes a SHA-256 file next to it.
4. Runs `gh release create` with the generated release notes (merged pull requests and commits since the previous release), the ZIP and the checksum file.

The workflow needs no secrets. It uses the built-in `GITHUB_TOKEN` with `contents: write`.

## Verify a download

```
sha256sum -c wikidata-neo4j-batchimporter-1.0.0.zip.sha256
unzip wikidata-neo4j-batchimporter-1.0.0.zip
wikidata-neo4j-batchimporter-1.0.0/wikidata-import.sh --help
```

The script looks for `tools.jar` in its own directory, so keep both files together.

## Versioning

The version is taken from the tag only. The version in `pom.xml` (`0.1.0-SNAPSHOT`) is not changed and `tools.jar` has the same name in every release.

## Fix a failed or wrong release

- **Workflow failed before the release was created:** delete the tag, fix the problem on `main`, tag again.

  ```
  git push --delete origin v1.0.0
  git tag -d v1.0.0
  ```

- **Release was created with wrong content:** delete the release together with its tag, then tag again.

  ```
  gh release delete v1.0.0 --cleanup-tag --yes
  git tag -d v1.0.0
  ```

Do not move a published tag. Use a new version number once users may have downloaded the release.
