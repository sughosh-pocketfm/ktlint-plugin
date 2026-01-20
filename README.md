# ktlintv2 - IntelliJ Plugin

A ktlint plugin for IntelliJ IDEA and Android Studio that properly handles custom rule JARs without class loader conflicts.

## Features

- Real-time Kotlin linting as you type
- Support for multiple custom rule JARs
- Support for compose-rules (nlopez)
- Quick fixes for common issues
- Format on save (optional)
- Configurable rule sets

## Building

```bash
./gradlew buildPlugin
```

The plugin ZIP will be in `build/distributions/`.

## Running in Sandbox

```bash
./gradlew runIde
```

## Installation

1. Build the plugin
2. In IntelliJ/Android Studio: Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select the ZIP from `build/distributions/`

## Configuration

After installation, go to: Settings → Tools → ktlintv2

### Adding Custom Rule JARs

1. Build your rule JAR (e.g., `linter.jar` from android_client)
2. Go to Settings → Tools → ktlintv2
3. Add the JAR path in "Additional Rule JARs" section

### Supported Rule JARs

- `linter.jar` - PocketFM custom rules
- `compose-rules-ktlint.jar` - nlopez/compose-rules
- Any ktlint rule JAR following RuleSetProviderV3

## Disabling Rules

In Settings → Tools → ktlintv2, add rule IDs to disable:

```
standard:no-wildcard-imports,pocketfm:magic-number
```

## Keyboard Shortcuts

- `Ctrl+Alt+Shift+L` - Format with ktlint

## Development

### Project Structure

```
src/main/kotlin/com/pocketfm/ktlintv2/
├── KtlintAnnotator.kt         # Real-time linting
├── KtlintV2Settings.kt        # Persistent settings
├── engine/
│   └── KtlintEngineWrapper.kt # ktlint engine wrapper
├── ui/
│   └── KtlintV2Configurable.kt # Settings UI
└── actions/
    ├── FormatAction.kt        # Format action
    └── LintAction.kt          # Lint action
```

### IntelliJ Platform SDK

- [Code Inspections](https://plugins.jetbrains.com/docs/intellij/code-inspections.html)
- [External Annotators](https://plugins.jetbrains.com/docs/intellij/external-annotator.html)
- [Settings](https://plugins.jetbrains.com/docs/intellij/settings.html)
