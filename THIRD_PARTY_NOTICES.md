# Third-party dependencies

No third-party implementation sources or JARs are vendored in this repository.
The parent and module POMs pin direct dependencies; platform packagers must retain
the license and notice files of all dependencies they redistribute or shade.

| Dependency | Version | Source / license |
| --- | --- | --- |
| OkHttp | 4.12.0 | https://github.com/square/okhttp — Apache-2.0 |
| Gson | 2.13.2 | https://github.com/google/gson — Apache-2.0 |
| Okio | Transitive from pinned OkHttp | https://github.com/square/okio — Apache-2.0 |
| Kotlin standard library | Transitive from pinned OkHttp | https://github.com/JetBrains/kotlin — Apache-2.0 |

The public API module has no external dependencies. Source contracts copied from
ShitBotUltra Core retain the project's AGPL-3.0-only license. Their repository,
reviewed tree and SHA-256 digests are recorded in `protocol/bridge-v1/SOURCE.json`.

This file does not claim that dependency resolution, builds, vulnerability scans
or runtime compatibility checks have been executed for this delivery.
