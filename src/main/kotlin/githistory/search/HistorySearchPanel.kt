package githistory.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.BoxLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class HistorySearchPanel(private val project: Project) : JPanel(BorderLayout()) {
    val queryField = JBTextField()
    private val modeBox = JComboBox(arrayOf(SearchMode.ADDED, SearchMode.DELETED))
    private val maxCount = JSpinner(SpinnerNumberModel(1, 1, 10_000, 1))
    private val status = JBLabel(" ")
    private val resultsPanel = JPanel(VerticalLayout(0))
    private var generation = 0

    init {
        queryField.emptyText.text = "Text to search"
        queryField.addActionListener { search() }
        modeBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val title = when (value) {
                    SearchMode.ADDED -> "Added"
                    SearchMode.DELETED -> "Removed"
                    else -> ""
                }
                return super.getListCellRendererComponent(list, title, index, isSelected, cellHasFocus)
            }
        }
        val searchButton = JButton("Search")
        searchButton.addActionListener { search() }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)))
        queryField.columns = 36
        toolbar.add(queryField)
        toolbar.add(modeBox)
        toolbar.add(JBLabel("Result limit"))
        toolbar.add(maxCount)
        toolbar.add(searchButton)

        resultsPanel.isOpaque = false
        val scroll = JBScrollPane(resultsPanel)
        scroll.border = JBUI.Borders.empty()
        status.border = JBUI.Borders.empty(4, 8)

        add(toolbar, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    private fun search() {
        val text = queryField.text
        if (text.isBlank()) {
            status.text = "Enter text"
            return
        }
        val mode = modeBox.selectedItem as SearchMode
        val limit = maxCount.value as Int
        val request = ++generation
        val severalRoots = GitRepositoryManager.getInstance(project).repositories.size > 1
        val labeledRoots = HashSet<VirtualFile>()
        var shown = 0
        var percent = 0
        resultsPanel.removeAll()
        resultsPanel.revalidate()
        resultsPanel.repaint()
        status.text = "Commits: 0 · Checked 0%"
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Git history search", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val outcome = GitPickaxeSearch.search(
                    project,
                    indicator,
                    text,
                    mode,
                    limit,
                    onCheckedPercent = { value ->
                        ApplicationManager.getApplication().invokeLater(
                            {
                                if (request == generation && !project.isDisposed) {
                                    percent = value
                                    status.text = "Commits: $shown · Checked $percent%"
                                }
                            },
                            ModalityState.defaultModalityState(),
                        )
                    },
                    onCommit = { root, commit ->
                        ApplicationManager.getApplication().invokeLater(
                            {
                                if (request != generation || project.isDisposed) {
                                    return@invokeLater
                                }
                                if (severalRoots && labeledRoots.add(root)) {
                                    resultsPanel.add(JBLabel(root.name).apply {
                                        border = JBUI.Borders.empty(8, 8, 0, 8)
                                    })
                                }
                                resultsPanel.add(commitView(root, commit))
                                shown++
                                status.text = "Commits: $shown · Checked $percent%"
                                resultsPanel.revalidate()
                                resultsPanel.repaint()
                            },
                            ModalityState.defaultModalityState(),
                        )
                    },
                )
                ApplicationManager.getApplication().invokeLater(
                    {
                        if (request != generation || project.isDisposed) {
                            return@invokeLater
                        }
                        val errors = outcome.errors
                        status.text = when {
                            errors.isNotEmpty() && outcome.commits == 0 -> errors.joinToString(" ")
                            errors.isNotEmpty() -> "Commits: ${outcome.commits}. ${errors.joinToString(" ")}"
                            outcome.commits == 0 -> "Nothing found"
                            else -> "Commits: ${outcome.commits}"
                        }
                    },
                    ModalityState.defaultModalityState(),
                )
            }
        })
    }

    private fun commitView(root: VirtualFile, commit: ParsedCommit): JComponent {
        val box = JPanel(VerticalLayout(JBUI.scale(2)))
        box.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(8),
        )
        val header = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        header.isOpaque = false
        header.add(link(commit.hash.take(8)) {
            CommitNavigator.openCommit(project, root, commit.hash)
        })
        header.add(JBLabel(formatDate(commit.dateIso)))
        header.add(JBLabel(commit.author))
        header.add(JBLabel(commit.subject))
        box.add(header)
        for (file in commit.files) {
            box.add(fileView(root, file))
            for (snippet in file.snippets) {
                box.add(snippetView(snippet))
            }
        }
        return box
    }

    private fun fileView(root: VirtualFile, file: ParsedFile): JComponent {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        row.isOpaque = false
        row.border = JBUI.Borders.emptyLeft(8)
        val ioFile = File(root.path, file.path)
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(ioFile.path)
        if (virtualFile?.isValid == true) {
            row.add(link(file.path) {
                CommitNavigator.openFile(project, virtualFile)
            })
        } else {
            row.add(JBLabel(file.path))
        }
        return row
    }

    private fun snippetView(lines: List<SnippetLine>): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.emptyLeft(16)
        panel.isOpaque = false
        val font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
        for ((text, matches) in lines) {
            val label = JLabel(text.ifEmpty { " " })
            label.font = font
            label.alignmentX = LEFT_ALIGNMENT
            if (matches) {
                label.isOpaque = true
                label.background = MATCH_BACKGROUND
            }
            panel.add(label)
        }
        return panel
    }

    private fun link(text: String, action: () -> Unit): JComponent {
        return LinkLabel(text, null, { _, _ -> action() }, Unit)
    }

    private fun formatDate(iso: String): String {
        val separator = iso.indexOf('T')
        if (separator < 0 || iso.length < separator + 6) {
            return iso
        }
        return iso.substring(0, separator) + " " + iso.substring(separator + 1, separator + 6)
    }

    private companion object {
        val MATCH_BACKGROUND: Color = JBColor(Color(0xFFF3C4), Color(0x5C4B1A))
    }
}
