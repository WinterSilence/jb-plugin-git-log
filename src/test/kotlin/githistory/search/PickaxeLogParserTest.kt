package githistory.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PickaxeLogParserTest {
    @Test
    fun `keeps one line around the matching diff line`() {
        val commits = PickaxeLogParser.parse("NEEDLE", lines(
            "COMMIT\u001Fabc123\u001FAnn\u001F2026-01-02T03:04:05+03:00\u001Fadd needle",
            "diff --git a/src/A.kt b/src/A.kt",
            "--- a/src/A.kt",
            "+++ b/src/A.kt",
            "@@ -1,5 +1,6 @@",
            " before2",
            " before1",
            "+NEEDLE",
            " after1",
            " after2",
            " after3",
        ))

        assertEquals(1, commits.size)
        val commit = commits.single()
        assertEquals("abc123", commit.hash)
        assertEquals("Ann", commit.author)
        assertEquals("2026-01-02T03:04:05+03:00", commit.dateIso)
        assertEquals("add needle", commit.subject)
        val file = commit.files.single()
        assertEquals("src/A.kt", file.path)
        val snippet = file.snippets.single()
        assertEquals(listOf(" before1", "+NEEDLE", " after1"), snippet.map { it.text })
        assertEquals(listOf(false, true, false), snippet.map { it.matches })
    }

    @Test
    fun `uses the old path when the file is deleted`() {
        val commits = PickaxeLogParser.parse("NEEDLE", lines(
            "COMMIT\u001Fdef456\u001FBob\u001F2026-02-03T04:05:06Z\u001Fdrop file",
            "diff --git a/src/Old.kt b/src/Old.kt",
            "deleted file mode 100644",
            "--- a/src/Old.kt",
            "+++ /dev/null",
            "@@ -1,3 +0,0 @@",
            "-line0",
            "-NEEDLE",
            "-line2",
        ))

        val file = commits.single().files.single()
        assertEquals("src/Old.kt", file.path)
        assertEquals(listOf("-line0", "-NEEDLE", "-line2"), file.snippets.single().map { it.text })
    }

    @Test
    fun `reads a quoted path with a space`() {
        val commits = PickaxeLogParser.parse("NEEDLE", lines(
            "COMMIT\u001Faaa\u001FAnn\u001F2026-03-04T05:06:07+03:00\u001Fspace",
            "diff --git \"a/src/my file.kt\" \"b/src/my file.kt\"",
            "--- \"a/src/my file.kt\"",
            "+++ \"b/src/my file.kt\"",
            "@@ -1 +1 @@",
            "-old",
            "+NEEDLE",
        ))

        assertEquals("src/my file.kt", commits.single().files.single().path)
    }

    @Test
    fun `merges overlapping match windows`() {
        val commits = PickaxeLogParser.parse("NEEDLE", lines(
            "COMMIT\u001Fbbb\u001FAnn\u001F2026-03-04T05:06:07+03:00\u001Ftwo",
            "diff --git a/F.kt b/F.kt",
            "@@ -1,6 +1,6 @@",
            " 0",
            "-NEEDLE",
            "+NEEDLE too",
            " 3",
            " 4",
        ))

        val snippet = commits.single().files.single().snippets.single()
        assertEquals(listOf(" 0", "-NEEDLE", "+NEEDLE too", " 3"), snippet.map { it.text })
        assertTrue(snippet[1].matches)
        assertTrue(snippet[2].matches)
    }

    @Test
    fun `matches the text as a literal string`() {
        val commits = PickaxeLogParser.parse("foo(", lines(
            "COMMIT\u001Fccc\u001FAnn\u001F2026-04-05T06:07:08Z\u001Fliteral",
            "diff --git a/A.kt b/A.kt",
            "@@ -1,2 +1,2 @@",
            "+foo(bar)",
            "+fooX",
        ))

        val snippet = commits.single().files.single().snippets.single()
        assertEquals(listOf("+foo(bar)", "+fooX"), snippet.map { it.text })
        assertEquals(listOf(true, false), snippet.map { it.matches })
    }

    @Test
    fun `added mode keeps only introduced lines`() {
        val commits = PickaxeLogParser.parse(
            "NEEDLE",
            lines(
                "COMMIT\u001Fddd\u001FAnn\u001F2026-04-05T06:07:08Z\u001Fboth",
                "diff --git a/A.kt b/A.kt",
                "@@ -1,2 +1,2 @@",
                "-NEEDLE",
                "+other",
            ),
            SearchMode.ADDED,
        )

        assertTrue(commits.isEmpty())
    }
}

private fun lines(vararg values: String): List<String> = values.toList()
