package com.surevine.sanitsation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.ChangeType;
import com.atlassian.stash.content.ChangesRequest;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.content.ContentService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.io.MoreSuppliers;
import com.atlassian.stash.io.TypeAwareOutputSupplier;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageUtils;
import com.surevine.sanitsation.service.SanitisationServiceFacade;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hook to perform sanitisation on commits to configured repositories.
 *
 * @author jonnyheavey
 *
 */
public class PreCommitSanitisationHook implements PreReceiveRepositoryHook {

    private static final Logger LOG = LoggerFactory.getLogger(PreCommitSanitisationHook.class);
    private static final String WORKING_DIR = "sanitisation";
    private static final int PAGE_REQUEST_LIMIT = 9999;

    private final ApplicationPropertiesService applicationPropertiesService;
    private final CommitService commitService;
    private final ContentService contentService;
    private final SanitisationArchiveWriter archiveWriter;

    public PreCommitSanitisationHook(
            ApplicationPropertiesService applicationPropertiesService,
            CommitService commitService,
            ContentService contentService) {

        this.applicationPropertiesService = applicationPropertiesService;
        this.commitService = commitService;
        this.contentService = contentService;

        Path tempArchiveDir = Paths.get(applicationPropertiesService.getTempDir().toString(), PreCommitSanitisationHook.WORKING_DIR);
        this.archiveWriter = new SanitisationArchiveWriter(tempArchiveDir);
    }

    @Override
    public boolean onReceive(@Nonnull RepositoryHookContext context,
            @Nonnull Collection<RefChange> refChanges, @Nonnull HookResponse hookResponse) {

        boolean allCommitsSane = true;

        for (RefChange refChange : refChanges) {

            final ChangesetsBetweenRequest request = new ChangesetsBetweenRequest.Builder(context.getRepository())
																	                .exclude(refChange.getFromHash())
																	                .include(refChange.getToHash())
																	                .build();

            final Page<Changeset> commits = commitService.getChangesetsBetween(request, PageUtils.newRequest(0, PAGE_REQUEST_LIMIT));
            for(Changeset commit: commits.getValues()) {
            	if(!isCommitSane(commit, context, hookResponse)) {
            		allCommitsSane = false;
            	}
            }
        }

        if(!allCommitsSane) {
        	hookResponse.out().println("Push rejected as not all commits passed sanitisation.");
        }

        return allCommitsSane;
    }

    /**
     * Determines whether a commit is safe according to sanitisation service
     * @param commit commit to be checked
     * @param context repository context the comit belongs to
     * @param hookResponse plugin response/output (for relaying messages to client)
     * @return
     */
    private boolean isCommitSane(Changeset commit, RepositoryHookContext context, HookResponse hookResponse) {

    	Set<Path> tempChangedFiles;
        Path changedFilesArchive;
        try {
			tempChangedFiles = getChangedFiles(context.getRepository(), commit);
			changedFilesArchive = archiveWriter.createArchive(tempChangedFiles);
		} catch (IOException | SanitisationException e) {
			LOG.error("Failed to create archive to sanitisation check.", e);
			return false;
		}

        SanitisationResult result;
		try {
			result = SanitisationServiceFacade.getInstance().isSane(changedFilesArchive,
																	commit.getMessage(),
																	context.getRepository().getProject().getKey(),
																	context.getRepository().getSlug(),
																	commit.getId());
		} catch (UnsupportedEncodingException e) {
			LOG.error("Failed to sanitise archive.", e);
			return false;
		}

        tidyTempFiles(commit, context, changedFilesArchive);

        if(!result.isSane()) {
            printCommitErrors(hookResponse, commit, result);
        }

        return result.isSane();
	}

	/**
     * Retrieves set of files in a repository that were changed in a commit.
     * @param repository repository to check
     * @param commit commit that changed files
     * @return
     * @throws IOException
     */
    private Set<Path> getChangedFiles(Repository repository, Changeset commit) throws IOException {

        Set<Path> changedFiles = new HashSet<Path>();
        final ChangesRequest changesRequest = new ChangesRequest.Builder(repository, commit.getId()).build();
        final Page<Change> changes = commitService.getChanges(changesRequest, PageUtils.newRequest(0, PAGE_REQUEST_LIMIT));

        Path commitTempDir = createTempDir(repository, commit);

        for(Change change: changes.getValues()) {
        	if(!ChangeType.DELETE.equals(change.getType())) {
            	Path tempChangedFile = createTempChangedFile(commitTempDir, repository, commit, change.getPath());
                changedFiles.add(tempChangedFile);
        	}
        }

        return changedFiles;
    }

    /**
     * Creates a temporary file (to be sanitised)
     * @param directory Directory to create temp file in
     * @param repository Repository that file belongs to
     * @param commit Commit that changed file
     * @param changedFile SCM file changed in commit
     * @return Path to temporary file
     * @throws IOException
     */
    private Path createTempChangedFile(Path directory,
    		Repository repository,
    		Changeset commit,
    		com.atlassian.stash.content.Path changedFile) throws IOException {

    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        TypeAwareOutputSupplier os = MoreSuppliers.newTypeAwareOutputSupplierOf(outputStream);

        contentService.streamFile(
                repository,
                commit.getId(),
                changedFile.toString(),
                os);

        String uuid = UUID.randomUUID().toString();
        Path tempFilePath = Paths.get(directory.toString(), uuid + ".txt");

        outputStream.writeTo(Files.newOutputStream(tempFilePath));

        return tempFilePath;
    }

    /**
     * Create a working directory for the commit's sanitisation check
     * @param repository Repository the commit belongs to
     * @param commit Commit that's being sanitised
     * @return
     * @throws IOException
     */
    private Path createTempDir(Repository repository, Changeset commit) throws IOException {

    	File stashTempDir = applicationPropertiesService.getTempDir();
    	Path tempFilesDir = Paths.get(stashTempDir.getPath(), WORKING_DIR, repository.getName() + "-" + commit.getId());
    	Files.createDirectories(tempFilesDir);

        return tempFilesDir;
    }

    /**
     * Delete the working directory for the commit's sanitisation check
     * @param repository Repository the commit belongs to
     * @param commit Commit that's being sanitised
     */
    private void deleteTempDir(Repository repository, Changeset commit) {
    	File stashTempDir = applicationPropertiesService.getTempDir();
    	Path tempFilesDir = Paths.get(stashTempDir.getPath(), WORKING_DIR, repository.getName() + "-" + commit.getId());
    	try {
			FileUtils.deleteDirectory(new File(tempFilesDir.toString()));
		} catch (IOException e) {
			LOG.warn("Failed to delete temporary working directory during sanitisation check: " + tempFilesDir.toString(), e);
		}
    }

    /**
     * Print the sanitisation errors for the commit back to the Git client.
     * @param hookResponse
     * @param commit
     * @param result
     */
	private void printCommitErrors(HookResponse hookResponse,
			Changeset commit, SanitisationResult result) {
		hookResponse.out().println(String.format("Commit %s (%s) failed sanitisation check:",
		                            commit.getDisplayId(),
		                            commit.getAuthor().getName()));
		for(String error: result.getErrors()) {
			hookResponse.out().println("Error: "+error.trim());
		}
	}

	/**
	 * Remove any temporary files created during sanitisation check
	 * @param commit
	 * @param context
	 * @param changedFilesArchive
	 */
	private void tidyTempFiles(Changeset commit, RepositoryHookContext context,
			Path changedFilesArchive) {
		try {
            deleteTempDir(context.getRepository(), commit);
            Files.deleteIfExists(changedFilesArchive);
        } catch (IOException e) {
            LOG.error(String.format("Failed to delete commit changed files archive (%s).",
                        changedFilesArchive.toString()), e);
        }
	}

}
