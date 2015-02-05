package com.surevine.sanitsation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

/**
 * Creates archives of files
 * @author jonnyheavey
 */
public class SanitisationArchiveWriter {

	private Path tempDir;
	private Path archivePath;

	public SanitisationArchiveWriter(Path tempDir) {
		this.tempDir = tempDir;
	}

	/**
	 * Create an archive of files
	 * @param archiveContentFilePaths paths of files to include in archive
	 * @return the archive
	 * @throws IOException
	 * @throws ArchiveException
	 * @throws CompressorException
	 */
	public Path createArchive(Set<Path> archiveContentFilePaths) throws IOException, ArchiveException, CompressorException {

		String uuid = UUID.randomUUID().toString();
        archivePath = Paths.get(tempDir.toString(), uuid + ".tar.gz");
        Path tarPath = Paths.get(tempDir.toString(), uuid + ".tar");

        createTar(tarPath, archiveContentFilePaths);
        writeGz(archivePath, tarPath);

        Files.deleteIfExists(tarPath);

		return archivePath;
	}

	/**
	 * Assembles .tar archive of files
	 * @param tarPath path to write tar file to
	 * @param filePaths files to be included in archive
	 */
	private void createTar(Path tarPath, Set<Path> filePaths) throws IOException, ArchiveException {

		ArchiveOutputStream os = new ArchiveStreamFactory()
        .createArchiveOutputStream("tar", Files.newOutputStream(tarPath));

		try {
		    for (Path path : filePaths) {
		        TarArchiveEntry entry = new TarArchiveEntry(path.toFile());
		        entry.setName(path.getFileName().toString());
		        os.putArchiveEntry(entry);
		        Files.copy(path, os);
		        os.closeArchiveEntry();
		    }
		} finally {
		    os.close();
		}
	}

	/**
	 * Compress tar archive with gzip
	 * @param gzPath path to write compressed archive to
	 * @param tarPath path of tar archive to compress
	 * @throws IOException
	 * @throws CompressorException
	 */
	private void writeGz(Path gzPath, Path tarPath) throws IOException, CompressorException {

		CompressorOutputStream cos = new CompressorStreamFactory()
        .createCompressorOutputStream("gz", Files.newOutputStream(gzPath));

		try {
		    Files.copy(tarPath, cos);
		} finally {
		    cos.close();
		}

		Files.deleteIfExists(tarPath);
	}

}
