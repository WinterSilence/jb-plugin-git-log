package githistory.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.impl.VcsProjectLog;

final class LogJump {
    private LogJump() {
    }

    static void jump(Project project, VirtualFile root, String hash) {
        var hashObject = project.getService(VcsLogObjectsFactory.class).createHash(hash);
        VcsProjectLog.showRevisionInMainLog(project, root, hashObject);
    }
}
