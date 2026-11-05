# Git History Search

A plugin for JetBrain IDE's like as PhpStorm that searches Git history for text entered by the user.

The Search in files tab is added to the Git tool window next to Log. The same Git menu that contains Show VCS Console Tab shows the tab while it is closed.
Modes:

- Added: `git log -S <text> -U1 --no-color`, then added lines that contain the text
- Removed: the same literal `-S` search, then deleted lines that contain the text

`<text>` is a literal string. Git is not given `--pickaxe-regex` or `-G`.

Each result shows a link to the commit (opens it in the Log tab), the date, the author, the subject, the file, and a diff fragment: the matching line plus one line before and one line after. The limit is the number of matching commits and defaults to 1. If the file exists in the working tree, its name opens the file in the editor. Otherwise the name stays plain text.

[Install from JetBrains Marketplace](https://plugins.jetbrains.com/plugin/35773-git-history-search)

## Build

JDK 25 is required. The build compiles against the installed PhpStorm 2026.2 (`/opt/devel/PhpStorm`): the archive from download.jetbrains.com currently returns HTTP 451. If the archive is available, replace `local(...)` with `phpstorm("2026.2.3")` in `build.gradle.kts`.

```bash
./gradlew test buildPlugin
```

Plugin archive in `build/distributions/`.

Install it from Settings | Plugins | "gear icon" | Install Plugin from Disk.

## Check in the IDE

```bash
./gradlew runIde
```

Open a project that uses Git, then open the Git tool window and the Search in files tab. Enter text that exists in the history and choose a mode.
