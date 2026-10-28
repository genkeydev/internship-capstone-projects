# Security Policy

## Overview

HealthLogger is a local service-health and event logging application developed as part of the GenKey Software Engineering Internship for Harbor Logistics.

This policy describes vulnerability reporting, runtime data handling, network exposure, secure development practices, and the limitations of the current implementation.

The policy applies to the current HealthLogger implementation and should be reviewed when the client requirements or deployment model change.

## Reporting a Vulnerability

The recommended reporting channel for HealthLogger is [GitHub Private Vulnerability Reporting](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/report-privately). A repository owner or administrator must enable this feature before it can be used. It allows a person to send a report privately to the repository maintainers instead of posting the details publicly.

To submit a report when the feature is enabled:

1. Open the HealthLogger repository on GitHub.
2. Open the **Security and quality** section. If it is not visible in the repository tabs, look for it in the dropdown menu.
3. Select **Report a vulnerability** and complete the private reporting form.

Do not report security vulnerabilities through public GitHub issues, pull requests, or public messages.

If **Report a vulnerability** is not available, do not post the vulnerability details publicly. Contact the project supervisor or repository maintainer through an approved private GenKey channel and ask for the designated way to submit the report. The repository owner or administrator should enable GitHub Private Vulnerability Reporting or confirm an approved alternative private channel.

A report should include:

- A clear description of the security issue and its potential impact.
- The affected component, file, or feature, if known.
- Steps to reproduce the issue, where safe to do so.
- Any suggested mitigation or fix, if available.

Do not include passwords, access tokens, API keys, real credentials, or other sensitive operational data in a report. Remove sensitive information from logs and screenshots before sharing them.

After a report is received, the project maintainer or designated security contact should acknowledge receipt through the private reporting channel, assess the issue, involve the appropriate developer or team, coordinate the fix, and keep the reporter updated. GenKey should set the expected acknowledgement time; this policy does not promise a response deadline that has not been approved.

Keep the report and technical details private while the issue is being investigated and fixed. Public disclosure should be coordinated by the maintainer and designated security contact, normally after a fix or suitable mitigation is available and has been checked.

## Event Store

HealthLogger stores runtime events as JSON Lines (JSONL) files. The default event store is:

`data/events.jsonl`

The event store location can also be configured through the `HEALTHLOGGER_STORE` environment variable or the applicable CLI `--store` option.

The rejected-event store may contain request metadata and validation reasons for rejected HTTP submissions. Treat accepted and rejected event files, along with runtime logs, as potentially sensitive operational data.

- Do not commit runtime event files, logs, or real operational data to Git.
- Keep runtime data local and restrict access to authorized users.
- Use synthetic or sanitized data for tests and examples.
- Do not commit passwords, access tokens, API keys, credentials, or other secrets.
- Review files before committing changes.
- Preserve the repository's intended `.gitignore` rules for runtime data.

If a credential or sensitive value is committed accidentally, revoke or rotate the affected credential immediately. Removing it from the latest file or commit does not remove it from Git history. When sensitive data has been exposed, coordinate with repository maintainers to purge it from Git history where appropriate. This may require rewriting commit history and performing a coordinated force-push. Notify affected collaborators before rewriting shared history, and verify that the exposed credential is no longer usable.

## HTTP Bind Address

The HealthLogger HTTP server binds to the loopback address `127.0.0.1` by default. This limits access to the local machine, but it is not a substitute for authentication or other security controls.

The current client specification does not require authentication or TLS for the local application.

Do not expose the server to a public or untrusted network without an explicit requirement and an appropriate security review. Any change to the bind address or deployment model must consider authentication, authorization, transport security, request-size limits, input validation, logging, configuration, and access to stored data.

New security requirements should be documented in the project specification and reflected in implementation and testing.

## Scope and Limitations

HealthLogger is a local service-health event logging and query application. It is not:

- A Security Information and Event Management (SIEM) platform.
- A multi-tenant Software-as-a-Service (SaaS) application.
- A centralized remote observability platform.
- A replacement for enterprise security monitoring.
- A publicly exposed production API by default.
- A healthcare information system.

The current implementation is designed around the local application requirements in the Harbor Logistics client specification. It does not provide every security control that might be required for an internet-facing or enterprise deployment.

Future client requirements must be reviewed and documented before expanding the application's scope or deployment model.

## Secure Development Practices

Development of HealthLogger should follow basic secure software development practices:

- Validate user-provided input before processing or storing it.
- Avoid hard-coded passwords, API keys, tokens, and other secrets.
- Keep runtime and sensitive data outside version control.
- Use synthetic data during development and testing.
- Review changes before committing them.
- Write tests for security-relevant validation rules.
- Record relevant security-related events through appropriate application logging.
- Keep dependencies and development tools reasonably up to date.
- Document new security requirements when the application scope changes.

## Security and Testing

Security-related requirements should be verified through testing where applicable.

Testing should include:

- Invalid and unexpected input.
- Boundary values.
- Missing and null required fields.
- Validation of data before storage.
- Verification that the HTTP server uses the intended loopback address.
- Verification that sensitive runtime files are not committed to Git.
- Verification that application configuration does not expose secrets.

Security tests should use synthetic or non-sensitive information.

## Changes to This Policy

This policy should be updated when the HealthLogger architecture, deployment model, data-handling requirements, or client security requirements change.

Significant security changes should be documented, tested, and reviewed before being incorporated into the project.

The bind address can be changed with the `HEALTHLOGGER_BIND` environment variable. The Docker setup sets `HEALTHLOGGER_BIND=0.0.0.0` inside the container only, so the published port can reach the process. `compose.yaml` publishes the port on `127.0.0.1:8088` on the host, so the API is still not reachable from other machines. Do not publish this port on other interfaces, because the API has no authentication.

