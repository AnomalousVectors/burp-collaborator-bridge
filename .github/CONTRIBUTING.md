# Contributing

Thanks for your interest in contributing.

## Before You Start

- Use Issues for bugs and feature requests
- Use GitHub Private Vulnerability Reporting for security issues
- Read the [README](../README.md) for project context and API usage
- Prefer the issue forms so reports include the triage details maintainers need

## Contribution Guidelines

- Keep changes minimal and focused
- Do not modify unrelated code
- Preserve existing formatting, structure, and layout
- Follow existing naming and project conventions
- Discuss larger changes before opening a pull request
- Prefer small pull requests that are easy to review and test

## Local checks

From the repository root (JDK 22+):

```bash
./gradlew compileJava compileTestJava
./gradlew spotlessCheck
./gradlew spotbugsMain spotbugsTest
./gradlew clean build
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

- Ensure the extension loads in Burp Suite without errors after JAR changes
- Validate UI changes visually
- Include reproduction steps for bug fixes where applicable
- Prefer `build/tmp/...` for test-created files instead of OS temp folders

## Good Reports and PRs

- Good bug reports usually include:
  - Burp version and edition
  - Java version and OS
  - Bridge host/port and whether Start succeeded
  - Sanitized request/response samples from `/health`, `/payload`, `/interactions`
- Good pull requests usually call out:
  - User-facing API or UI impact
  - Any Montoya API version assumptions

## Pull Requests

- Clearly describe what changed and why
- Reference related issues if applicable
- Include screenshots for UI changes
