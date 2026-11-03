package githistory.search

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content

class HistorySearchContentProvider(private val project: Project) : ChangesViewContentProvider {
    override fun initTabContent(content: Content) {
        val panel = HistorySearchPanel(project)
        content.isCloseable = true
        SearchInFilesTab.trackClosing(project, content)
        content.component = panel
        content.preferredFocusableComponent = panel.queryField
    }
}
