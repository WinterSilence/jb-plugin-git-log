package githistory.search

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object CommitNavigator {
    fun openCommit(project: Project, root: VirtualFile, hash: String) {
        val toolWindow = ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.VCS_LOG)
        val showLog = Runnable {
            ChangesViewContentManager.getInstance(project).selectContent(ChangesViewContentManager.VCS_LOG)
            LogJump.jump(project, root, hash)
        }
        if (toolWindow == null) {
            showLog.run()
        } else {
            toolWindow.show(showLog)
        }
    }

    fun openFile(project: Project, file: File) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
}
