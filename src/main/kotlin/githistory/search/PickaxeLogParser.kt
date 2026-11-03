package githistory.search

object PickaxeLogParser {
    private const val COMMIT_PREFIX = "COMMIT\u001F"
    private const val CONTEXT_LINES = 1

    fun parse(text: String, lines: List<String>, mode: SearchMode? = null): List<ParsedCommit> {
        val commits = mutableListOf<ParsedCommit>()
        var commit: CommitBuilder? = null
        var file: FileBuilder? = null
        var hunk: MutableList<String>? = null

        fun flushHunk() {
            val currentFile = file ?: return
            val linesInHunk = hunk ?: return
            val snippets = snippetsAroundMatches(linesInHunk, text, mode)
            if (snippets.isNotEmpty()) {
                currentFile.snippets += snippets
            }
            hunk = null
        }

        fun flushFile() {
            flushHunk()
            val currentCommit = commit
            val currentFile = file
            if (currentCommit != null && currentFile != null && currentFile.snippets.isNotEmpty()) {
                currentCommit.files += currentFile.build()
            }
            file = null
        }

        fun flushCommit() {
            flushFile()
            val current = commit
            if (current != null && current.files.isNotEmpty()) {
                commits += current.build()
            }
            commit = null
        }

        for (line in lines) {
            val header = parseCommitHeader(line)
            if (header != null) {
                flushCommit()
                commit = header
                continue
            }
            if (line.startsWith("diff --git ")) {
                flushFile()
                file = parseDiffHeader(line)
                continue
            }
            val currentFile = file ?: continue
            if (line.startsWith("@@")) {
                flushHunk()
                hunk = mutableListOf()
                continue
            }
            if (hunk != null) {
                if (!line.startsWith("\\")) {
                    hunk!!.add(line)
                }
                continue
            }
            when {
                line.startsWith("+++ ") -> {
                    val path = diffPath(line.removePrefix("+++ "))
                    if (path == "/dev/null") {
                        currentFile.path = currentFile.oldPath
                    } else {
                        currentFile.path = path
                    }
                }
                line.startsWith("--- ") -> {
                    currentFile.oldPath = diffPath(line.removePrefix("--- "))
                    if (currentFile.path == "/dev/null") {
                        currentFile.path = currentFile.oldPath
                    }
                }
            }
        }
        flushCommit()
        return commits
    }

    private fun snippetsAroundMatches(
        hunkLines: List<String>,
        text: String,
        mode: SearchMode?,
    ): List<List<SnippetLine>> {
        if (hunkLines.isEmpty() || text.isEmpty()) {
            return emptyList()
        }
        val matchIndexes = hunkLines.indices.filter { index -> isHit(hunkLines[index], text, mode) }
        if (matchIndexes.isEmpty()) {
            return emptyList()
        }
        val last = hunkLines.lastIndex
        val ranges = matchIndexes
            .map { index -> (index - CONTEXT_LINES).coerceAtLeast(0)..(index + CONTEXT_LINES).coerceAtMost(last) }
            .fold(mutableListOf<IntRange>()) { merged, range ->
                val previous = merged.lastOrNull()
                if (previous != null && previous.last + 1 >= range.first) {
                    merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
                } else {
                    merged += range
                }
                merged
            }
        return ranges.map { range ->
            range.map { index ->
                val line = hunkLines[index]
                SnippetLine(line, isHit(line, text, mode))
            }
        }
    }

    private fun isHit(diffLine: String, text: String, mode: SearchMode?): Boolean {
        if (!payload(diffLine).contains(text)) {
            return false
        }
        val sign = diffLine.firstOrNull() ?: return false
        return when (mode) {
            SearchMode.ADDED -> sign == '+'
            SearchMode.DELETED -> sign == '-'
            null -> sign == '+' || sign == '-'
        }
    }

    private fun payload(diffLine: String): String {
        if (diffLine.isEmpty()) {
            return diffLine
        }
        return when (diffLine[0]) {
            '+', '-', ' ' -> diffLine.substring(1)
            else -> diffLine
        }
    }

    private fun parseCommitHeader(line: String): CommitBuilder? {
        if (!line.startsWith(COMMIT_PREFIX)) {
            return null
        }
        val parts = line.split('\u001F')
        if (parts.size < 5 || parts[1].isEmpty()) {
            return null
        }
        return CommitBuilder(
            hash = parts[1],
            author = parts[2],
            dateIso = parts[3],
            subject = parts.subList(4, parts.size).joinToString("\u001F"),
        )
    }

    private fun parseDiffHeader(line: String): FileBuilder? {
        val paths = readTwoPaths(line.removePrefix("diff --git ")) ?: return FileBuilder("", "")
        val oldPath = stripSidePrefix(paths.first)
        val newPath = stripSidePrefix(paths.second)
        val path = if (newPath == "/dev/null") oldPath else newPath
        return FileBuilder(path, oldPath)
    }

    private fun diffPath(tokenWithMaybeTimestamp: String): String {
        val token = tokenWithMaybeTimestamp.substringBefore('\t').trim()
        val raw = if (token.startsWith("\"")) unquote(token) else token
        return stripSidePrefix(raw)
    }

    private fun stripSidePrefix(path: String): String {
        if (path == "/dev/null") {
            return path
        }
        if (path.startsWith("a/") || path.startsWith("b/")) {
            return path.substring(2)
        }
        return path
    }

    private fun readTwoPaths(spec: String): Pair<String, String>? {
        val first = readPath(spec, 0) ?: return null
        if (first.second >= spec.length || spec[first.second] != ' ') {
            return null
        }
        val second = readPath(spec, first.second + 1) ?: return null
        return first.first to second.first
    }

    private fun readPath(spec: String, start: Int): Pair<String, Int>? {
        if (start >= spec.length) {
            return null
        }
        if (spec[start] != '"') {
            val space = spec.indexOf(' ', start)
            val end = if (space < 0) spec.length else space
            return spec.substring(start, end) to end
        }
        val endQuote = findClosingQuote(spec, start + 1) ?: return null
        return unquote(spec.substring(start, endQuote + 1)) to (endQuote + 1)
    }

    private fun findClosingQuote(spec: String, start: Int): Int? {
        var index = start
        while (index < spec.length) {
            if (spec[index] == '\\') {
                index += 2
                continue
            }
            if (spec[index] == '"') {
                return index
            }
            index++
        }
        return null
    }

    private fun unquote(quoted: String): String {
        if (quoted.length < 2 || quoted.first() != '"' || quoted.last() != '"') {
            return quoted
        }
        val body = quoted.substring(1, quoted.length - 1)
        val result = StringBuilder()
        var index = 0
        while (index < body.length) {
            val char = body[index]
            if (char != '\\' || index + 1 >= body.length) {
                result.append(char)
                index++
                continue
            }
            val next = body[index + 1]
            when (next) {
                'n' -> result.append('\n')
                't' -> result.append('\t')
                '\\', '"' -> result.append(next)
                in '0'..'7' -> {
                    val octal = StringBuilder()
                    var digit = index + 1
                    while (digit < body.length && digit < index + 4 && body[digit] in '0'..'7') {
                        octal.append(body[digit])
                        digit++
                    }
                    result.append(octal.toString().toInt(8).toChar())
                    index = digit
                    continue
                }
                else -> result.append(next)
            }
            index += 2
        }
        return result.toString()
    }

    private class CommitBuilder(
        val hash: String,
        val author: String,
        val dateIso: String,
        val subject: String,
    ) {
        val files = mutableListOf<ParsedFile>()

        fun build(): ParsedCommit = ParsedCommit(hash, author, dateIso, subject, files.toList())
    }

    private class FileBuilder(var path: String, var oldPath: String) {
        val snippets = mutableListOf<List<SnippetLine>>()

        fun build(): ParsedFile = ParsedFile(path, snippets.toList())
    }
}
