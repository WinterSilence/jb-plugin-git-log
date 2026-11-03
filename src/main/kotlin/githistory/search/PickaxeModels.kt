package githistory.search

data class ParsedCommit(
    val hash: String,
    val author: String,
    val dateIso: String,
    val subject: String,
    val files: List<ParsedFile>,
)

data class ParsedFile(
    val path: String,
    val snippets: List<List<SnippetLine>>,
)

data class SnippetLine(
    val text: String,
    val matches: Boolean,
)
