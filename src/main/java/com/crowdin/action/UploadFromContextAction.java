package com.crowdin.action;

import com.crowdin.client.Crowdin;
import com.crowdin.client.CrowdinProjectCacheProvider;
import com.crowdin.client.CrowdinProperties;
import com.crowdin.client.CrowdinPropertiesLoader;
import com.crowdin.util.FileUtil;
import com.crowdin.util.GitUtil;
import com.crowdin.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.crowdin.Constants.MESSAGES_BUNDLE;
import static com.crowdin.Constants.STANDARD_TRANSLATION_PATTERN;

/**
 * Created by ihor on 1/27/17.
 */
public class UploadFromContextAction extends BackgroundAction {
    @Override
    public void performInBackground(AnActionEvent anActionEvent) {
        final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(anActionEvent.getDataContext());
        Project project = anActionEvent.getProject();
        try {
            CrowdinProperties properties = CrowdinPropertiesLoader.load(project);
            Crowdin crowdin = new Crowdin(project, properties.getProjectId(), properties.getApiToken(), properties.getBaseUrl());
            String branch = properties.isDisabledBranches() ? "" : GitUtil.getCurrentBranch(project);

            Optional<String> sourcePattern = properties.getSourcesWithPatterns().keySet()
                .stream()
                .filter(s -> FileUtil.getSourceFilesRec(project.getBaseDir(), s).contains(file))
                .findAny();
            if (sourcePattern.isPresent()) {
                crowdin.uploadFile(file, properties.getSourcesWithPatterns().get(sourcePattern.get()), branch);
            } else {
                throw new RuntimeException("Unexpected error: couldn't find suitable source pattern");
            }
            CrowdinProjectCacheProvider.outdateBranch(branch);
        } catch (Exception e) {
            NotificationUtil.showErrorMessage(project, e.getMessage());
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
        boolean isSourceFile = false;
        try {
            CrowdinProperties properties;
            properties = CrowdinPropertiesLoader.load(project);
            List<VirtualFile> files = properties.getSourcesWithPatterns().keySet()
                .stream()
                .flatMap(s -> FileUtil.getSourceFilesRec(project.getBaseDir(), s).stream())
                .collect(Collectors.toList());
            isSourceFile = files.contains(file);
        } catch (Exception exception) {
//            do nothing
        } finally {
            e.getPresentation().setEnabled(isSourceFile);
            e.getPresentation().setVisible(isSourceFile);
        }
    }

    @Override
    String loadingText(AnActionEvent e) {
        return String.format(MESSAGES_BUNDLE.getString("labels.loading_text.upload_sources_from_context"), CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext()).getName());
    }
}
