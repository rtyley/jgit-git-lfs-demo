/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class PullCommandTest extends RepositoryTestCase {
	/** Second Test repository */
	protected FileRepository dbTarget;

	private Git source;

	private Git target;

	private File sourceFile;

	private File targetFile;

	public void testPullFastForward() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus().equals(
				MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the source file
		writeToFile(sourceFile, "Another change");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Some change in remote").call();

		res = target.pull().call();

		assertFalse(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertEquals(res.getMergeResult().getMergeStatus(),
				MergeStatus.FAST_FORWARD);
		assertFileContentsEqual(targetFile, "Another change");
	}

	public void testPullConflict() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus().equals(
				MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the source file
		writeToFile(sourceFile, "Source change");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Source change in remote").call();

		// change the target file
		writeToFile(targetFile, "Target change");
		target.add().addFilepattern("SomeFile.txt").call();
		target.commit().setMessage("Target change in local").call();

		res = target.pull().call();

		String sourceChangeString = "Source change\n>>>>>>> branch 'refs/heads/master' of "
				+ target.getRepository().getConfig().getString("remote",
						"origin", "url");

		assertFalse(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertEquals(res.getMergeResult().getMergeStatus(),
				MergeStatus.CONFLICTING);
		String result = "<<<<<<< HEAD\nTarget change\n=======\n"
				+ sourceChangeString + "\n";
		assertFileContentsEqual(targetFile, result);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		dbTarget = createWorkRepository();
		source = new Git(db);
		target = new Git(dbTarget);

		// put some file in the source repo
		sourceFile = new File(db.getWorkTree(), "SomeFile.txt");
		writeToFile(sourceFile, "Hello world");
		// and commit it
		source.add().addFilepattern("SomeFile.txt").call();
		RevCommit commit = source.commit().setMessage(
				"Initial commit for source").call();

		// point the master branch to the new commit
		RefUpdate upd = dbTarget.updateRef("refs/heads/master");
		upd.setNewObjectId(commit.getId());
		upd.update();

		// configure the target repo to connect to the source via "origin"
		StoredConfig targetConfig = dbTarget.getConfig();
		targetConfig.setString("branch", "master", "remote", "origin");
		targetConfig
				.setString("branch", "master", "merge", "refs/heads/master");
		RemoteConfig config = new RemoteConfig(targetConfig, "origin");

		config
				.addURI(new URIish(source.getRepository().getWorkTree()
						.getPath()));
		config.addFetchRefSpec(new RefSpec(
				"+refs/heads/*:refs/remotes/origin/*"));
		targetConfig.save();
		config.update(targetConfig);

		targetFile = new File(dbTarget.getWorkTree(), "SomeFile.txt");
		writeToFile(targetFile, "Hello world");
		// make sure we have the same content
		target.pull().call();
	}

	private void writeToFile(File actFile, String string) throws IOException {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(actFile);
			fos.write(string.getBytes("UTF-8"));
			fos.close();
		} finally {
			if (fos != null)
				fos.close();
		}
	}

	private void assertFileContentsEqual(File actFile, String string)
			throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		FileInputStream fis = null;
		byte[] buffer = new byte[100];
		try {
			fis = new FileInputStream(actFile);
			int read = fis.read(buffer);
			while (read > 0) {
				bos.write(buffer, 0, read);
				read = fis.read(buffer);
			}
			String content = new String(bos.toByteArray(), "UTF-8");
			assertEquals(string, content);
		} finally {
			if (fis != null)
				fis.close();
		}
	}
}
