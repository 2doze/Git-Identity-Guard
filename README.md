# Git Identity Guard

English | [简体中文](README.zh-CN.md)

Git Identity Guard is an IntelliJ IDEA plugin that helps prevent the wrong Git identity from reaching GitHub commits. It links to the GitHub accounts already configured in the IDE, detects repositories owned by those accounts, and checks the effective `user.name` and `user.email` before you commit.

The plugin never changes Git configuration silently. Applying a suggested identity always requires an explicit action, and changes are limited to the current repository.

## Features

- Discovers GitHub.com accounts from IntelliJ IDEA without asking you to configure another token.
- Recognizes common HTTPS, SCP-style SSH, and `ssh://` GitHub remote URLs.
- Checks every Git root in a project when the project opens and when repository remotes change.
- Fetches and caches each account's GitHub no-reply email, with an ID-based fallback when the email API is unavailable.
- Validates both the effective Git email and user name against the matched GitHub account.
- Shows a warning with actions to apply the privacy identity or ignore that repository.
- Provides a pre-commit check, enabled by default, that can switch the identity, continue once, or cancel the commit.
- Supports multiple GitHub accounts, multiple remotes, verified-email leniency, a custom email allowlist, and repository-specific ignores.
- Localizes the plugin UI in English and Simplified Chinese according to the IDE language.
- Performs identity comparisons locally, has no telemetry, and only writes repository-local Git configuration.

## How It Works

1. The plugin reads the GitHub.com accounts configured in IntelliJ IDEA.
2. It inspects every fetch and push remote URL. A repository is protected when a remote owner matches a logged-in GitHub user name, case-insensitively. An `origin` match has priority when several remotes match.
3. It obtains the account's no-reply email from GitHub. If `/user/emails` does not provide one or is unavailable to the current token, the plugin derives the modern `<id>+<login>@users.noreply.github.com` address from `/user`.
4. It reads the effective `user.name` and `user.email` for that Git root, respecting normal Git configuration precedence.
5. If either field does not match, the plugin displays a warning. Choosing **Apply Privacy Identity** writes only the fields that need correction with `git config --local`.

No repository is modified merely because it was scanned.

## Requirements

- IntelliJ IDEA 2024.2 or later (build 242+).
- The bundled Git and GitHub plugins must be enabled.
- At least one GitHub.com account must be added under **Settings/Preferences > Version Control > GitHub**.
- JDK 21 is required only when building the plugin from source.

## Installation

### Option 1: Install from JetBrains Marketplace (recommended)

Search for **Git Identity Guard** under **Settings/Preferences > Plugins > Marketplace**, or open the [plugin page](https://plugins.jetbrains.com/plugin/33265-git-identity-guard) and pick your IDE from the **Install to...** menu. Restart the IDE when prompted.

### Option 2: Build from source

1. Clone the repository and run `./gradlew buildPlugin` (`.\gradlew.bat buildPlugin` on Windows). JDK 21 is required; see [Building from Source](#building-from-source) for details.
2. In IntelliJ IDEA, open **Settings/Preferences > Plugins**, open the gear menu, and select **Install Plugin from Disk...**.
3. Select the generated ZIP from `build/distributions/` without extracting it, and restart the IDE when prompted.

## Getting Started

1. Add your GitHub.com account in **Settings/Preferences > Version Control > GitHub** if it is not already configured.
2. Open a project containing a Git repository whose GitHub remote owner is that account.
3. When a mismatch is detected, use the notification to apply the suggested privacy identity or ignore the repository.
4. Review plugin options under **Settings/Preferences > Tools > Git Identity Guard**.

The pre-commit check is enabled by default. When it detects a mismatch, you can switch the identity and continue, use the current identity for that commit, or cancel the commit.

## Configuration

| Setting | Default | Behavior |
| --- | --- | --- |
| Pre-commit check | On | Checks protected Git roots before a commit and asks what to do when the identity is wrong. |
| Show current email in notifications | On | Includes the effective email and user name in mismatch notifications. |
| Auto-enable pre-commit check on mismatch | Off | Turns the pre-commit check back on after a mismatch is found. |
| Lenient mode | Off | Accepts a verified email belonging to the matched GitHub account or an email in the allowlist. The Git user name must still match. |
| Email allowlist | Empty | Adds one accepted email per line when lenient mode is enabled. |
| GitHub account cache | Automatic | Shows the cached privacy email and fetch status for each account; **Refresh** retries retrieval and rechecks repositories. |
| Ignored repositories | Empty | Lists ignored repository/account pairs and lets you remove an ignore. |

Settings are application-wide, while ignored repositories are stored per project.

## Privacy and Safety

- GitHub API calls use credentials managed by IntelliJ IDEA. The plugin does not store your access token.
- The plugin calls GitHub's `/user/emails` and `/user` endpoints only to resolve privacy and verified email information.
- Resolved email data is cached in the IDE to avoid unnecessary requests; comparisons happen locally.
- There is no telemetry or third-party service.
- Applying a fix changes only `user.name` and/or `user.email` in the affected repository's local config. Global Git configuration is never changed.

## Supported Scope and Limitations

Supported GitHub.com remote forms include:

```text
https://github.com/owner/repository.git
git@github.com:owner/repository.git
ssh://git@github.com/owner/repository.git
```

The current version intentionally does not protect:

- GitHub Enterprise Server hosts.
- SSH host aliases such as `git@gh:owner/repository.git`, because the IDE cannot infer the alias target from the remote string alone.
- `git://` remotes.
- Repositories whose remote owner does not match one of the logged-in account names, including organization-owned repositories.

## Troubleshooting

### No warning appears

- Confirm the remote points to `github.com` and its owner matches a GitHub account configured in the IDE.
- Check **Settings/Preferences > Appearance & Behavior > Notifications > Git Identity Guard** and make sure notifications are not disabled.
- Open **Tools > Git Identity Guard** and refresh the GitHub account cache.
- Check whether the repository is in the ignored repositories list.

### The email looks correct but the plugin still reports a mismatch

The protected identity is a pair: the no-reply email and the GitHub login used as `user.name`. A mismatch in either field triggers protection. Lenient mode relaxes only the email check.

### Privacy email retrieval fails

Open the plugin settings and review the account cache status. Re-authenticate the GitHub account if the token has expired, then select **Refresh**. A token that cannot access `/user/emails` normally falls back to the ID-based no-reply address.

## Building from Source

The project uses Kotlin, Gradle, and the IntelliJ Platform Gradle Plugin. Use JDK 21 and the included Gradle wrapper.

PowerShell on Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat buildPlugin
```

macOS or Linux:

```bash
./gradlew test
./gradlew buildPlugin
```

Useful development tasks:

```text
runIde        Launch a sandbox IDE with the plugin installed
verifyPlugin  Verify compatibility against the configured IDE versions
```

The installable ZIP is written to `build/distributions/`.

## Contributing

Bug reports and focused pull requests are welcome. For behavior changes, include tests for the affected matching, identity, or state-management logic and run the test suite before submitting.

## License

Licensed under the [Apache License 2.0](LICENSE).
