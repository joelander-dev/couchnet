# Couchnet

## Releasing

- Both the release tag and the commit it points at must be signed with the maintainer's PGP key (`D42FE7F0C574AD4471B23FD9539EE47EFF51FCA7`). Local git already signs commits and tags by default.
- Never tag a commit that GitHub signed, such as a merge commit made with the GitHub web UI (signed by `web-flow`). If `main` ends on one of those, first make a locally signed commit on top of it, such as a version bump or changelog entry, push it, and then tag that commit.
- Before pushing a tag, check both signatures: `git verify-commit <tag>^{commit}` and `git verify-tag <tag>`.
- Tag format: annotated and signed, `git tag -s vX.Y.Z -m "Couchnet X.Y.Z"`.
- Pushing a tag runs `.github/workflows/release.yml`, which builds the signed APK and attaches it to the GitHub release. Create the release with notes (`gh release create <tag> --verify-tag --notes-file …`) right after pushing the tag, so the workflow uploads the APK to that release instead of making a release with no notes.
