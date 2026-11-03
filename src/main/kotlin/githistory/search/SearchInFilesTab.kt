package githistory.search

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import git4idea.repo.GitRepositoryManager
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Predicate

object SearchInFilesTab {
    const val TAB_NAME = "Search in files"
    private const val SETTING = "git.history.search.tab.visible"
    private val tracked = Collections.synchronizedMap(WeakHashMap<Project, Boolean>())

    fun isEnabled(project: Project): Boolean {
        return PropertiesComponent.getInstance(project).getBoolean(SETTING, true)
    }

    fun trackClosing(project: Project, content: Content) {
        val manager = content.manager ?: return
        synchronized(tracked) {
            if (tracked.put(project, true) != null) {
                return
            }
        }
        manager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                val application = ApplicationManager.getApplication()
                if (project.isDisposed || application.isDisposed) {
                    return
                }
                if (event.content.displayName != TAB_NAME) {
                    return
                }
                PropertiesComponent.getInstance(project).setValue(SETTING, false, true)
            }
        })
    }

    fun show(project: Project) {
        PropertiesComponent.getInstance(project).setValue(SETTING, true, true)
        val manager = ChangesViewContentManager.getInstance(project)
        val content = manager.findContent(TAB_NAME) ?: createContent(project).also { manager.addContent(it) }
        content.isCloseable = true
        trackClosing(project, content)
        manager.setSelectedContent(content)
        val toolWindowId = ChangesViewContentManager.getToolWindowIdFor(project, TAB_NAME)
        ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.show(null)
    }

    private fun createContent(project: Project): Content {
        val panel = HistorySearchPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, TAB_NAME, false)
        content.isCloseable = true
        content.preferredFocusableComponent = panel.queryField
        return content
    }
}

class ShowSearchInFilesTabAction : DumbAwareAction("Search in files", null, SearchInFilesIcons.ACTION) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val open = project != null &&
            ChangesViewContentManager.getInstance(project).findContent(SearchInFilesTab.TAB_NAME) != null
        event.presentation.icon = SearchInFilesIcons.ACTION
        event.presentation.isEnabledAndVisible = project != null && !open
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        SearchInFilesTab.show(project)
    }
}

class GitRepoVisibility : Predicate<Project> {
    override fun test(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty() &&
            SearchInFilesTab.isEnabled(project)
    }
}
