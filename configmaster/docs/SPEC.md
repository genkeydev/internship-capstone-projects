# ConfigMaster CLI

## Specification

**Tetteh Abraham Nartey**
GenKey Internship
September 18, 2026

## Purpose

Northwind Payments deploys the same service across three environments. Its platform team currently copies JSON configuration files manually, resulting in missing configuration keys in production.

ConfigMaster is a command-line tool that loads JSON configuration layers in order, merges nested objects, validates the effective configuration against Northwind's client-provided specification, and prints the merged JSON to stdout for use in deployment scripts. It supports configurable merge strategies, including appending feature flags and rejecting conflicting region values. Operational logs and errors go to stderr so stdout remains suitable for piping into deployment workflows.

## Scope

### In scope for v1

- Loading JSON config files in the order given on the command line
  (defaults, then environment overlay, then an optional local override)
- Deep-merging the loaded layers into one effective document
- Field-level merge behavior read from the schema at runtime:
  `append_list` for list fields that should be combined across layers,
  `fail_on_conflict` for fields that must not silently change between layers,
  and `override` when the later layer should replace the earlier value
- Validating the effective document against the required fields and types
  defined in `config/client-spec.json`
- Three commands: `validate`, `merge`, and `show`, all of which load and merge
  the given files before acting

For the current Northwind Payments client specification (`config/client-spec.json`), the merge strategies are:

- `app.features`: `append_list` — concatenate feature flags from configuration layers in order.
- `app.region`: `fail_on_conflict` — reject different values supplied by different layers.
- All other fields: `override` — the later layer's value takes precedence.

These rules are defined by the client specification, not hard-coded into the Java implementation. When the client specification changes, ConfigMaster must apply the updated rules at runtime.

### Supported field types

`fieldTypes` supports the following type names:

- `string`
- `integer`
- `number`
- `boolean`
- `array`
- `object`

As a v1 requirement, any unsupported `fieldTypes` value makes the client specification invalid. The CLI must exit with code `2`, report the error to stderr, and leave stdout empty.

**Current scaffold limitation:** `SchemaValidator.typeMatches` currently accepts unknown type names instead of rejecting them. This behavior does not yet meet the v1 requirement and must be corrected in a later implementation week, not as part of this Week 1 documentation PR.

### Out of scope for v1

- YAML or any format other than JSON
- Remote or HTTP-based configuration sources
- Encryption of secrets
- A graphical interface

## Command-line interface

### Usage

```text
configmaster validate|merge|show [--spec path] file.json [file.json ...]
```

By default, ConfigMaster loads `config/client-spec.json`. A different specification can be selected using the `CONFIGMASTER_SPEC` environment variable or the `--spec` command-line option.

If more than one is provided, `--spec` takes priority over `CONFIGMASTER_SPEC`. If neither is provided, the default specification is used.

All three commands accept one or more files and merge them from left to right.
`validate` does not require a single file; it validates the merged result of
everything supplied.

## Acceptance criteria

### Merging configuration files

- Configuration files are processed from left to right. When two layers contain nested objects, their fields are merged individually instead of replacing the entire object.
- For `app.features`, lists are combined in the order they appear. Both values must be arrays; otherwise, the CLI reports an issue.
- For `app.region`, different values cause a merge conflict. If both values are the same, the merge succeeds and keeps the existing value.
- For other fields, the later value takes precedence.
- The merged configuration must contain `app.name` and `app.port`, with the types defined in the client specification.
- A required field set to JSON `null` counts as present in v1. The current validator does not treat `null` as a missing value and skips type checking for it. For example, `"app": {"name": null}` satisfies the presence check for `app.name`.
- The CLI reports every merge conflict and validation issue rather than stopping at the first one.
- The supported merge strategies are `override`, `append_list`, and `fail_on_conflict`. Any other strategy makes the client specification invalid. The CLI must report the error to stderr, leave stdout empty, and exit with code `2`.

**Current scaffold limitation:** `Merger` currently treats unknown merge strategies as `override`. This does not meet the v1 requirement and will need to be corrected in a later implementation week.

### `validate`

- Loads the supplied configuration files, merges them, and checks the result against the client specification.
- On success, exits with code `0`, writes `valid` to stderr, and leaves stdout empty.
- If there are merge conflicts or validation issues, reports all issues to stderr, exits with code `1`, and leaves stdout empty.
- If a file or specification is missing, or the JSON is malformed, reports the error to stderr, exits with code `2`, and leaves stdout empty.
- Missing commands, missing file arguments, and unknown commands also result in exit code `2`, with an error message on stderr.

### `merge` and `show`

- Both commands must load, merge, and validate the supplied configuration files in the same way.
- On success, both commands must print the merged configuration as pretty-printed JSON to stdout and exit with code `0`.
- Progress information and logs must go to stderr, keeping stdout free of anything other than the resulting JSON.
- If there are merge conflicts or validation issues, both commands must report all issues to stderr, leave stdout empty, and exit with code `1`.
- If an input file or specification is missing, or the JSON is malformed, both commands must report the error to stderr, leave stdout empty, and exit with code `2`.
- In v1, `merge` and `show` are required to behave identically. This allows Northwind's deployment scripts to use `show` to retrieve the effective configuration.

## Stream integrity

- A pipe or redirect, for example:

  ```text
  configmaster show base.json prod.json > effective.json
  ```

  produces a file containing only JSON and no log text mixed in
- All logging, including files loaded, spec used, and issue counts, goes to
  stderr on every run

## Adopting a new client spec JSON

**File path:** `config/client-spec.json`, overridable via `--spec` or
`CONFIGMASTER_SPEC`

**Ownership:** Northwind Payments' platform team owns the client specification and provides updates when its configuration requirements change. GenKey maintains the ConfigMaster tool, but the client decides which fields and validation rules it needs.

The CLI parses the specification on every invocation of `validate`, `merge`, or
`show`. Field names, types, and required properties are read at runtime, so
adopting a schema change means replacing the specification file without adding
those field names to the Java implementation.

### Schema version

The client specification includes a `schemaVersion` field, currently set to `1.0.0`. ConfigMaster supports schema versions in the `1.x.x` format and rejects missing, blank, malformed, or unsupported versions. An invalid schema version causes the CLI to exit with code `2`, report the error to stderr, and leave stdout empty. This validation was added as the Week 6 client-driven extension.

A replacement specification can reuse the supported field types and merge
strategies without changing Java code. If a future specification introduces a
new field type or merge behavior, support for that behavior must be implemented
explicitly rather than being silently accepted.
