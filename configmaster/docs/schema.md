# ConfigMaster CLI: Client Spec JSON Schema

**Tetteh Abraham Nartey**
GenKey Internship
September 29, 2026

## Purpose

This document describes the structure of `config/client-spec.json`, the file that drives ConfigMaster's validation and merge behavior at runtime.

The current client is Northwind Payments, based on the brief provided by Priya Shah, the platform lead. GenKey is the organization hosting the internship. The field names in the specification, such as `app.features` and `app.region`, come from Northwind's requirements and are used to show how ConfigMaster handles different configuration rules.

The schema is data-driven. Required fields, expected types, and merge strategies are read from the JSON file rather than hard-coded as field names in Java.

## How the spec is located

ConfigMaster uses `config/client-spec.json` by default.

The specification path can also be supplied through the `CONFIGMASTER_SPEC` environment variable or explicitly with:

```text
--spec path
```

An explicit `--spec` argument takes precedence over the environment/default location.

## Configuration layers

ConfigMaster accepts one or more JSON configuration files and merges them from left to right in the order they are supplied on the command line.

A typical deployment may use:

1. defaults
2. environment overlay
3. local override

These roles are not hard-coded in ConfigMaster. The CLI treats each input as another configuration layer.

Values from later layers are merged into earlier layers according to the configured merge strategy.

The final result is called the effective merged document.

## Schema components

### `schemaVersion`

Identifies the version of the client specification.

Current scaffold value:

```json
"schemaVersion": "1.0.0"
```

ConfigMaster checks the `schemaVersion` when loading the client specification. It accepts versions in the `1.x.x` format, such as `1.0.0` and `1.2.3`. Missing, blank, malformed, or unsupported versions are rejected. If the version is invalid, the CLI reports an error to stderr, leaves stdout empty, and exits with code `2`.

### `client`

Identifies the client associated with the specification.

Current scaffold value:

```json
"client": "Northwind Platform"
```

`Northwind Platform` refers to the current client, Northwind Payments. The client details come from the brief provided by Priya Shah, Northwind's platform lead.

ConfigMaster loads the `client` value as part of the specification, but it uses the other schema fields to decide how configuration files should be validated and merged.

### `requiredFields`

An array of dotted key paths that must be present in the effective merged document.

Current sample values:

```json
"requiredFields": [
  "app.name",
  "app.port"
]
```

A required field fails validation when it is missing from the merged configuration.

However, the current validator treats a field containing JSON `null` as present. It also skips type checking for `null` values. This means a required field set to `null` currently passes validation, even though the value may not be useful to the application.

Dotted paths refer to values inside nested JSON objects. For example, `app.port` refers to:

```json
{
  "app": {
    "port": 8080
  }
}
```

A deeper path such as:

```text
app.database.host
```

would refer to:

```json
{
  "app": {
    "database": {
      "host": "db.example"
    }
  }
}
```

### `fieldTypes`

A map from dotted key paths to their expected data types.

The currently supported type names are:

- `string`
- `integer`
- `number`
- `boolean`
- `array`
- `object`

Current sample values:

```json
"fieldTypes": {
  "app.name": "string",
  "app.port": "integer",
  "app.region": "string",
  "app.features": "array"
}
```

A field is type-checked only when it is present in the configuration and listed in `fieldTypes`.

Missing optional fields are not treated as type errors.

For example:

```json
"app.port": "integer"
```

means this is valid:

```json
{
  "app": {
    "port": 8080
  }
}
```

while this is invalid because the value is a string:

```json
{
  "app": {
    "port": "8080"
  }
}
```

The validator checks values against the six supported types listed above. However, if the specification contains an unknown type name, the current implementation accepts any JSON value for that field. This happens because `SchemaValidator.typeMatches` returns `true` for unrecognized types.

This is a limitation of the current implementation. Adding proper support for a new type would require changes to the Java code.

### `mergeStrategies`

Defines how ConfigMaster handles a field when more than one configuration layer provides a value for the same dotted path.

Current sample values:

```json
"mergeStrategies": {
  "app.features": "append_list",
  "app.region": "fail_on_conflict"
}
```

Merge strategies are selected by dotted path.

ConfigMaster first looks for an explicit strategy for the current path in `mergeStrategies`. If none exists, it uses `defaultMergeStrategy`. If no default is supplied, the implementation falls back to `override`.

When both layers contain JSON objects at the same key, ConfigMaster merges their contents field by field. It does this before applying any merge strategy for that path. A merge strategy is applied when at least one of the two values is not an object.

The current implementation also treats an unknown merge strategy as `override`. This means an unrecognized strategy name does not currently produce an error.

#### `override`

The later layer's value replaces the earlier value.

This is the default behavior when a path has no explicit strategy.

Base layer:

```json
{
  "app": {
    "port": 8080
  }
}
```

Overlay layer:

```json
{
  "app": {
    "port": 9090
  }
}
```

Effective result:

```json
{
  "app": {
    "port": 9090
  }
}
```

#### `append_list`

Arrays from each layer are concatenated in layer order instead of replacing one another.

Base layer:

```json
{
  "app": {
    "features": ["metrics"]
  }
}
```

Overlay layer:

```json
{
  "app": {
    "features": ["tracing"]
  }
}
```

Effective result:

```json
{
  "app": {
    "features": ["metrics", "tracing"]
  }
}
```

If either value is not an array, ConfigMaster records a merge issue.

#### `fail_on_conflict`

The `fail_on_conflict` strategy compares the values from both layers. If they are different, ConfigMaster reports a merge conflict instead of replacing the existing value.

If the values are equal, there is no conflict, and the existing value is kept. This also applies when both values are JSON `null`. However, comparing `null` with a value such as `"eu-west-1"` causes a conflict.

Base layer:

```json
{
  "app": {
    "region": "eu-west-1"
  }
}
```

Overlay layer:

```json
{
  "app": {
    "region": "us-east-1"
  }
}
```

This produces a conflict on `app.region`.

The merger continues processing so that additional issues can also be reported. When merge or validation issues exist, the CLI exits with code `1`.

The current sample specification uses `fail_on_conflict` for `app.region`.

### `defaultMergeStrategy`

Defines the strategy used for a field that has no explicit entry in `mergeStrategies`.

Current value:

```json
"defaultMergeStrategy": "override"
```

If no default strategy is supplied, the implementation also falls back to `override`.

## Invalid JSON input

The schema rules apply only after a configuration file has been successfully parsed as JSON.

If an input file contains malformed JSON, loading fails before merge or schema validation can complete.

For example, this incomplete JSON cannot be loaded:

```json
{
  "app": {
    "port": 8080
```

ConfigMaster reports the parsing error to `stderr` and exits with code `2`.

## Empty configuration input

A JSON file whose root value is `null` is treated by the loader as an empty configuration document.

Empty configuration layers can still participate in a merge. The final effective document may still fail validation if required fields are missing.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Successful run with no merge or validation issues |
| `1` | Merge conflict or schema validation issue |
| `2` | Usage, loading, or malformed-input error |

For `merge` and `show`, successful merged JSON is printed only when no merge or validation issues exist.

## Current sample specification

The current scaffold file at `config/client-spec.json` contains:

```json
{
  "schemaVersion": "1.0.0",
  "client": "Northwind Platform",
  "requiredFields": ["app.name", "app.port"],
  "fieldTypes": {
    "app.name": "string",
    "app.port": "integer",
    "app.region": "string",
    "app.features": "array"
  },
  "mergeStrategies": {
    "app.features": "append_list",
    "app.region": "fail_on_conflict"
  },
  "defaultMergeStrategy": "override"
}
```

This is the current specification for Northwind Payments. It defines the required fields, expected data types, and merge rules based on the client's requirements.

The field paths, including `app.name`, `app.port`, `app.region`, and `app.features`, are read from the specification at runtime. They are not hard-coded into the Java implementation, so a future client can provide different fields without changing the existing field names in the code.

## Dynamic adoption

To add or remove required fields, change expected field types, or assign different existing merge strategies, update `config/client-spec.json`.

Field paths such as `app.name`, `app.port`, `app.region`, and `app.features` are not hard-coded into the Java implementation. ConfigMaster reads those paths from the specification at runtime.

The implementation does define the behavior of the supported type names and merge strategies. Introducing a genuinely new validation type or a new merge strategy with new behavior therefore requires a Java code change.

A changed specification file itself does not require its field names to be added to or recompiled into the Java source.
