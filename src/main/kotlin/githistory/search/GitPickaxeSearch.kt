package githistory.search

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager

enum class SearchMode {
    ADDED,
    DELETED,
}

data class RepoSearchResult(
    val root: VirtualFile,
    val commits: List<ParsedCommit>,
    val error: String?,
)

data class SearchOutcome(
    val commits: Int,
    val errors: List<String>,
    val stoppedByLimit: Boolean,
)

object GitPickaxeSearch {
    private const val PRETTY = "--pretty=format:COMMIT%x1f%H%x1f%an%x1f%aI%x1f%s"
    private const val CHUNK = 200

    fun search(
        project: Project,
        indicator: ProgressIndicator,
        text: String,
        mode: SearchMode,
        maxCount: Int,
        onCheckedPercent: (Int) -> Unit,
        onCommit: (VirtualFile, ParsedCommit) -> Unit,
    ): SearchOutcome {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        val histories = repositories.map { repository ->
            indicator.checkCanceled()
            repository.root to listCommits(project, repository.root)
        }
        val total = histories.sumOf { (_, history) -> history.hashes.size }
        indicator.isIndeterminate = false
        var checked = 0
        var remaining = maxCount
        var foundCount = 0
        val errors = mutableListOf<String>()
        var stoppedByLimit = false
        for ((root, history) in histories) {
            indicator.checkCanceled()
            if (remaining <= 0) {
                stoppedByLimit = checked < total
                break
            }
            if (history.error != null) {
                errors += history.error
                report(indicator, checked, total, onCheckedPercent)
                continue
            }
            if (history.hashes.isEmpty()) {
                report(indicator, checked, total, onCheckedPercent)
                continue
            }
            for (chunk in history.hashes.chunked(CHUNK)) {
                indicator.checkCanceled()
                if (remaining <= 0) {
                    stoppedByLimit = checked < total
                    break
                }
                val logged = logChunk(project, root, text, mode, chunk)
                if (logged.error != null) {
                    checked += chunk.size
                    errors += logged.error
                    report(indicator, checked, total, onCheckedPercent)
                    break
                }
                val accepted = logged.commits.take(remaining)
                for (commit in accepted) {
                    onCommit(root, commit)
                }
                foundCount += accepted.size
                remaining -= accepted.size
                checked += chunk.size
                report(indicator, checked, total, onCheckedPercent)
            }
            if (remaining <= 0) {
                stoppedByLimit = checked < total
                break
            }
        }
        return SearchOutcome(foundCount, errors.toList(), stoppedByLimit)
    }

    private fun report(indicator: ProgressIndicator, checked: Int, total: Int, onCheckedPercent: (Int) -> Unit) {
        val percent = if (total <= 0) 100 else (checked * 100 / total).coerceIn(0, 100)
        indicator.fraction = if (total <= 0) 1.0 else checked.toDouble() / total
        indicator.text = "Checked $percent%"
        onCheckedPercent(percent)
    }

    private fun listCommits(project: Project, root: VirtualFile): CommitHistory {
        val listed = run(project, root, GitCommand.REV_LIST, listOf("HEAD"))
        if (!listed.success()) {
            return CommitHistory(emptyList(), gitError(listed))
        }
        val hashes = listed.output.map { it.trim() }.filter { it.isNotEmpty() }
        return CommitHistory(hashes, null)
    }

    private fun logChunk(
        project: Project,
        root: VirtualFile,
        text: String,
        mode: SearchMode,
        hashes: List<String>,
    ): RepoSearchResult {
        val handler = GitLineHandler(project, root, GitCommand.LOG)
        handler.setSilent(true)
        handler.withNoTty()
        handler.addParameters(PRETTY, "-U1", "--no-color", "--no-walk", "-S$text")
        handler.addParameters(hashes)
        return try {
            val result = Git.getInstance().runCommand(handler)
            if (!result.success()) {
                RepoSearchResult(root, emptyList(), gitError(result))
            } else {
                RepoSearchResult(root, PickaxeLogParser.parse(text, result.output, mode), null)
            }
        } catch (canceled: ProcessCanceledException) {
            throw canceled
        } catch (error: Exception) {
            RepoSearchResult(root, emptyList(), error.message ?: error.toString())
        }
    }

    private fun run(project: Project, root: VirtualFile, command: GitCommand, parameters: List<String>): GitCommandResult {
        val handler = GitLineHandler(project, root, command)
        handler.setSilent(true)
        handler.withNoTty()
        handler.addParameters(parameters)
        return Git.getInstance().runCommand(handler)
    }

    private fun gitError(result: GitCommandResult): String {
        return result.errorOutputAsJoinedString.ifBlank { "git exited with code ${result.exitCode}" }
    }

    private data class CommitHistory(val hashes: List<String>, val error: String?)
}
